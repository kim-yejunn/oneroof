package com.oneroof.payment;

import java.util.List;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.intake.PaymentResultReceived;
import com.oneroof.payment.PaymentGatewayPort.PaymentOrder;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns approved payment requests into instructions, hands them to the bank, and records the
 * bank's reply. Each step is its own transaction started by a stored event, so an interruption
 * leaves the instruction in a known state and the step is retried.
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

	@ApplicationModuleListener
	void on(PaymentRequested requested) {
		if (this.instructions.findByReferenceTypeAndReferenceId(requested.referenceType(), requested.referenceId())
			.isPresent()) {
			return;
		}
		PaymentInstruction instruction = this.instructions
			.save(new PaymentInstruction(requested, this.clock.today()));
		this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "PAYMENT_INSTRUCTED", SUBJECT,
				String.valueOf(instruction.getId()),
				"%s %s 건 %,d원을 %s 에 지급하도록 지시 생성".formatted(requested.referenceType(), requested.referenceId(),
						requested.amountKrw(), requested.agencyId()),
				Provenance.CALCULATED, requested.correlationId()));
		this.events.publishEvent(new PaymentInstructionCreated(instruction.getId()));
	}

	@ApplicationModuleListener
	void on(PaymentInstructionCreated created) {
		PaymentInstruction instruction = this.instructions.findById(created.instructionId()).orElseThrow();
		if (instruction.getStatus() != PaymentStatus.REQUESTED) {
			return;
		}
		String gatewayReference = this.gateway
			.send(new PaymentOrder(instruction.getId(), instruction.getAgencyId(), instruction.getAmount().krw()));
		instruction.markSent(gatewayReference);
		this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "PAYMENT_SENT", SUBJECT,
				String.valueOf(instruction.getId()),
				"은행 전송%s, 접수번호 %s".formatted(this.gateway.isSimulated() ? "(모의 처리)" : "", gatewayReference),
				this.gateway.isSimulated() ? Provenance.MOCK_REPLY : Provenance.CALCULATED,
				instruction.getCorrelationId()));
	}

	@ApplicationModuleListener
	void on(PaymentResultReceived result) {
		PaymentInstruction instruction = this.instructions.findById(result.instructionId())
			.orElseThrow(() -> new IllegalArgumentException("지급 지시 #%d 이 없음".formatted(result.instructionId())));
		instruction.recordBankReply(result.succeeded(), result.bankReference());
		this.auditLog.record(new AuditRecord("EXTERNAL:BANK", result.succeeded() ? "PAYMENT_CONFIRMED" : "PAYMENT_FAILED",
				SUBJECT, String.valueOf(instruction.getId()),
				"은행 회신: %s, 거래번호 %s".formatted(result.succeeded() ? "이체 완료" : "이체 실패", result.bankReference()),
				Provenance.MOCK_REPLY, instruction.getCorrelationId()));
		if (result.succeeded()) {
			this.events.publishEvent(new PaymentCompleted(instruction.getId(), instruction.getReferenceType(),
					instruction.getReferenceId(), instruction.getCorrelationId()));
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
