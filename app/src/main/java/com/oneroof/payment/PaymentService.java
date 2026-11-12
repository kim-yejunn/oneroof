package com.oneroof.payment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.intake.PaymentResultsReceived;
import com.oneroof.payment.PaymentGatewayPort.BulkReceipt;
import com.oneroof.payment.PaymentGatewayPort.PaymentOrder;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns approved payment requests into instructions, hands them to the bank as one bulk transfer,
 * and applies the bank's reply. Each step handles a whole batch in one transaction started by one
 * stored event, so an interruption leaves every instruction in a known state and the step is retried.
 */
@Service
public class PaymentService {

	private static final String SUBJECT = "PaymentInstruction";

	private final PaymentInstructionRepository instructions;
	private final PaymentGatewayPort gateway;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	PaymentService(PaymentInstructionRepository instructions, PaymentGatewayPort gateway,
			ApplicationEventPublisher events, AuditLog auditLog, BusinessClock clock) {
		this.instructions = instructions;
		this.gateway = gateway;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	/** Creates one instruction per requested payment, skipping references already instructed. */
	@ApplicationModuleListener
	void on(PaymentsRequested requested) {
		List<PaymentInstruction> created = new ArrayList<>();
		Map<String, List<PaymentsRequested.Payment>> byType = requested.payments()
			.stream()
			.collect(Collectors.groupingBy(PaymentsRequested.Payment::referenceType));
		byType.forEach((referenceType, payments) -> {
			Set<String> existing = this.instructions
				.findByReferenceTypeAndReferenceIdIn(referenceType,
						payments.stream().map(PaymentsRequested.Payment::referenceId).toList())
				.stream()
				.map(PaymentInstruction::getReferenceId)
				.collect(Collectors.toSet());
			payments.stream()
				.filter((payment) -> !existing.contains(payment.referenceId()))
				.map((payment) -> new PaymentInstruction(payment, this.clock.today()))
				.forEach(created::add);
		});
		if (created.isEmpty()) {
			return;
		}
		for (PaymentInstruction instruction : this.instructions.saveAll(created)) {
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "PAYMENT_INSTRUCTED", SUBJECT,
					String.valueOf(instruction.getId()),
					"%s %s 건 %,d원을 %s 에 지급하도록 지시 생성".formatted(instruction.getReferenceType(),
							instruction.getReferenceId(), instruction.getAmount().krw(), instruction.getAgencyId()),
					Provenance.CALCULATED, instruction.getCorrelationId()));
		}
		this.events.publishEvent(new PaymentBatchCreated(created.stream().map(PaymentInstruction::getId).toList()));
	}

	/** Hands every instruction of the batch that has not been sent yet to the bank as one bulk transfer. */
	@ApplicationModuleListener
	void on(PaymentBatchCreated created) {
		List<PaymentInstruction> toSend = this.instructions.findByIdInOrderByIdAsc(created.instructionIds())
			.stream()
			.filter((instruction) -> instruction.getStatus() == PaymentStatus.REQUESTED)
			.toList();
		if (toSend.isEmpty()) {
			return;
		}
		BulkReceipt receipt = this.gateway.sendBulk(toSend.stream()
			.map((instruction) -> new PaymentOrder(instruction.getId(), instruction.getAgencyId(),
					instruction.getAmount().krw()))
			.toList());
		Provenance provenance = this.gateway.isSimulated() ? Provenance.MOCK_REPLY : Provenance.CALCULATED;
		for (int i = 0; i < toSend.size(); i++) {
			PaymentInstruction instruction = toSend.get(i);
			instruction.markSent(receipt.orderReferences().get(i));
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "PAYMENT_SENT", SUBJECT,
					String.valueOf(instruction.getId()),
					"은행 대량이체 %s 에 포함해 전송%s (%d건 중 %d번째), 접수번호 %s".formatted(receipt.bulkReference(),
							this.gateway.isSimulated() ? "(모의 처리)" : "", toSend.size(), i + 1,
							receipt.orderReferences().get(i)),
					provenance, instruction.getCorrelationId()));
		}
	}

	/**
	 * Applies a bank reply. An instruction that is no longer waiting for a reply, for example because
	 * an earlier reply already settled it, is recorded and skipped rather than failing the whole reply.
	 */
	@ApplicationModuleListener
	void on(PaymentResultsReceived received) {
		Map<Long, PaymentInstruction> byId = this.instructions
			.findByIdInOrderByIdAsc(received.results().stream().map(PaymentResultsReceived.Result::instructionId).toList())
			.stream()
			.collect(Collectors.toMap(PaymentInstruction::getId, Function.identity()));
		List<PaymentsCompleted.Paid> paid = new ArrayList<>();
		for (PaymentResultsReceived.Result result : received.results()) {
			PaymentInstruction instruction = byId.get(result.instructionId());
			if (instruction == null || instruction.getStatus() != PaymentStatus.SENT) {
				this.auditLog.record(new AuditRecord("EXTERNAL:BANK", "PAYMENT_REPLY_IGNORED", SUBJECT,
						String.valueOf(result.instructionId()),
						instruction == null ? "없는 지급 지시에 대한 회신이라 반영하지 않음"
								: "이미 %s 상태라 회신을 반영하지 않음".formatted(instruction.getStatus()),
						Provenance.MOCK_REPLY, instruction == null ? received.eventId() : instruction.getCorrelationId()));
				continue;
			}
			instruction.recordBankReply(result.succeeded(), result.bankReference());
			this.auditLog.record(new AuditRecord("EXTERNAL:BANK",
					result.succeeded() ? "PAYMENT_CONFIRMED" : "PAYMENT_FAILED", SUBJECT,
					String.valueOf(instruction.getId()),
					"은행 회신: %s, 거래번호 %s".formatted(result.succeeded() ? "이체 완료" : "이체 실패",
							result.bankReference()),
					Provenance.MOCK_REPLY, instruction.getCorrelationId()));
			if (result.succeeded()) {
				paid.add(new PaymentsCompleted.Paid(instruction.getId(), instruction.getReferenceType(),
						instruction.getReferenceId(), instruction.getCorrelationId()));
			}
		}
		if (!paid.isEmpty()) {
			this.events.publishEvent(new PaymentsCompleted(paid));
		}
	}

	@Transactional(readOnly = true)
	public List<PaymentInstruction> instructionsFor(String correlationId) {
		return this.instructions.findByCorrelationIdOrderByIdAsc(correlationId);
	}

	@Transactional(readOnly = true)
	public List<PaymentInstruction> awaitingBankReply() {
		return this.instructions.findByStatusOrderByIdAsc(PaymentStatus.SENT);
	}
}
