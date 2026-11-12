package com.oneroof.payment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oneroof.approval.ApprovalRequestsSubmitted;
import com.oneroof.approval.ApprovalRequestsWithdrawn;
import com.oneroof.approval.ApprovalsDecided;
import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditReferral;
import com.oneroof.audit.AuditRecord;
import com.oneroof.clarification.ClarificationClosed;
import com.oneroof.clarification.ClarificationKind;
import com.oneroof.clarification.ClarificationOutcome;
import com.oneroof.clarification.ClarificationsRequested;
import com.oneroof.intake.PaymentResultsReceived;
import com.oneroof.payment.PaymentGatewayPort.BulkReceipt;
import com.oneroof.payment.PaymentGatewayPort.PaymentOrder;
import com.oneroof.receivables.Deposit;
import com.oneroof.receivables.OverdueBalanceChanged;
import com.oneroof.receivables.ReceivableOffsetsRequested;
import com.oneroof.receivables.ReceivableOffsetsReversed;
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
 *
 * <p>Before anything is sent, an agency with long-overdue receivables goes to finance, which decides
 * whether to set the rebate off against them or pay it and collect separately (see
 * {@link OverdueOffsetPolicy}). Then the payee account is checked (see {@link PayeeAccountCheck}).
 */
@Service
public class PaymentService {

	private static final String SUBJECT = "PaymentInstruction";

	/** Approval requests asking finance to decide an offset. The subject id is the instruction id. */
	public static final String OFFSET_SUBJECT = "OffsetProposal";

	private final PaymentInstructionRepository instructions;
	private final PayeeAccountCheck payeeAccounts;
	private final OverdueOffsetPolicy offsetPolicy;
	private final PaymentGatewayPort gateway;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	PaymentService(PaymentInstructionRepository instructions, PayeeAccountCheck payeeAccounts,
			OverdueOffsetPolicy offsetPolicy, PaymentGatewayPort gateway, ApplicationEventPublisher events,
			AuditLog auditLog, BusinessClock clock) {
		this.instructions = instructions;
		this.payeeAccounts = payeeAccounts;
		this.offsetPolicy = offsetPolicy;
		this.gateway = gateway;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	/**
	 * Creates one instruction per requested payment, skipping references already instructed. An
	 * agency with long-overdue receivables goes to finance first; otherwise a payment whose payee
	 * account is unconfirmed is held and the agency is asked to confirm it, and the rest go to the bank.
	 */
	@ApplicationModuleListener
	void on(PaymentsRequested requested) {
		LocalDate today = this.clock.today();
		List<Deposit> deposits = this.payeeAccounts.depositsUpTo(today);
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
			payments.stream().filter((payment) -> !existing.contains(payment.referenceId())).forEach((payment) -> {
				Optional<OverdueOffsetPolicy.Proposal> offset = this.offsetPolicy.enabled()
						? this.offsetPolicy.proposalFor(payment.agencyId(), payment.amountKrw()) : Optional.empty();
				if (offset.isPresent()) {
					created.add(new PaymentInstruction(payment, offset.get().offsetKrw(), offset.get().basis(), today));
				}
				else {
					PayeeAccountCheck.Result payee = this.payeeAccounts.check(payment.agencyId(), today, deposits);
					created.add(new PaymentInstruction(payment, payee.payeeAccount(), payee.holdReason(), today));
				}
			});
		});
		if (created.isEmpty()) {
			return;
		}
		Outcome outcome = new Outcome();
		for (PaymentInstruction instruction : this.instructions.saveAll(created)) {
			if (instruction.getStatus() == PaymentStatus.OFFSET_REVIEW) {
				this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "OFFSET_PROPOSED", SUBJECT,
						String.valueOf(instruction.getId()),
						"%s %s 건 %,d원 중 %,d원 상계를 회계·자금팀에 상신 — %s (상계 조항·90일 기준은 교육용 가정)".formatted(
								instruction.getReferenceType(), instruction.getReferenceId(), instruction.getAmount().krw(),
								instruction.getOffset().krw(), instruction.getOffsetBasis()),
						Provenance.ASSUMPTION, instruction.getCorrelationId()));
				outcome.approvals.add(offsetRequest(instruction, null));
			}
			else {
				recordRouted(instruction, outcome);
			}
		}
		outcome.publish();
	}

	/**
	 * Finance decided offsets. Approving sets the rebate off against the overdue receivables (no more
	 * than is still overdue now) and notifies the agency; rejecting pays the whole amount and leaves
	 * collection to finance. After a dispute, approving keeps the offset and rejecting cancels it.
	 */
	@ApplicationModuleListener
	void on(ApprovalsDecided decided) {
		List<ApprovalsDecided.Decision> decisions = decided.decisions()
			.stream()
			.filter((decision) -> OFFSET_SUBJECT.equals(decision.subjectType()))
			.toList();
		if (decisions.isEmpty()) {
			return;
		}
		Map<Long, PaymentInstruction> byId = this.instructions
			.findByIdInOrderByIdAsc(decisions.stream().map((decision) -> Long.valueOf(decision.subjectId())).toList())
			.stream()
			.collect(Collectors.toMap(PaymentInstruction::getId, Function.identity()));
		LocalDate today = this.clock.today();
		List<Deposit> deposits = this.payeeAccounts.depositsUpTo(today);
		Outcome outcome = new Outcome();
		for (ApprovalsDecided.Decision decision : decisions) {
			PaymentInstruction instruction = byId.get(Long.valueOf(decision.subjectId()));
			String comment = decision.comment().isBlank() ? "" : " — " + decision.comment();
			if (instruction.getStatus() == PaymentStatus.OFFSET_REVIEW) {
				decideFirstRound(instruction, decision.approved(), comment, today, deposits, outcome);
			}
			else if (instruction.getStatus() == PaymentStatus.OFFSET_DISPUTED) {
				decideDispute(instruction, decision.approved(), comment, today, deposits, outcome);
			}
			else {
				this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "OFFSET_DECISION_IGNORED", SUBJECT,
						decision.subjectId(), "이미 %s 상태라 상계 결정을 반영하지 않음".formatted(instruction.getStatus()),
						Provenance.CALCULATED, instruction.getCorrelationId()));
			}
		}
		outcome.publish();
	}

	private void decideFirstRound(PaymentInstruction instruction, boolean approved, String comment, LocalDate today,
			List<Deposit> deposits, Outcome outcome) {
		String id = String.valueOf(instruction.getId());
		if (!approved) {
			instruction.waiveOffset("회계·자금팀 판단: 전액 지급 후 미수금 별도 회수" + comment);
			this.auditLog.record(new AuditRecord(Role.FINANCE.name(), "OFFSET_WAIVED", SUBJECT, id,
					"상계하지 않고 %,d원 전액 지급, 미수금은 별도 회수%s".formatted(instruction.getAmount().krw(), comment),
					Provenance.MOCK_REPLY, instruction.getCorrelationId()));
			routeToPayee(instruction, today, deposits, outcome);
			return;
		}
		long available = this.offsetPolicy.proposalFor(instruction.getAgencyId(), instruction.getAmount().krw())
			.map(OverdueOffsetPolicy.Proposal::offsetKrw)
			.orElse(0L);
		long offset = Math.min(instruction.getOffset().krw(), available);
		if (offset == 0) {
			instruction.waiveOffset("상계 결정 시점에 남은 연체 잔액이 없어 전액 지급" + comment);
			this.auditLog.record(new AuditRecord(Role.FINANCE.name(), "OFFSET_WAIVED", SUBJECT, id,
					"상계를 결정했으나 남은 %d일 초과 연체가 없어 전액 지급".formatted(this.offsetPolicy.overdueDaysOver()),
					Provenance.CALCULATED, instruction.getCorrelationId()));
			routeToPayee(instruction, today, deposits, outcome);
			return;
		}
		instruction.settleOffset(offset, "회계·자금팀 상계 결정 %,d원%s".formatted(offset, comment));
		this.auditLog.record(new AuditRecord(Role.FINANCE.name(), "OFFSET_SETTLED", SUBJECT, id,
				"%s 판매장려금 %,d원 중 %,d원을 연체 미수금과 상계, 잔여 지급 %,d원. 몰수가 아니라 같은 금액의 미수금이 함께 소멸함%s"
					.formatted(instruction.getAgencyId(), instruction.getAmount().krw(), offset,
							instruction.getNetAmount().krw(), comment),
				Provenance.CALCULATED, instruction.getCorrelationId()));
		outcome.offsets.add(new ReceivableOffsetsRequested.Offset(reference(instruction), instruction.getAgencyId(),
				offset, this.offsetPolicy.overdueDaysOver(), instruction.getCorrelationId()));
		outcome.notices.add(offsetNotice(instruction));
		if (instruction.getStatus() == PaymentStatus.OFFSET_SETTLED) {
			outcome.completed.add(paid(instruction));
		}
		else {
			routeToPayee(instruction, today, deposits, outcome);
		}
	}

	private void decideDispute(PaymentInstruction instruction, boolean approved, String comment, LocalDate today,
			List<Deposit> deposits, Outcome outcome) {
		String id = String.valueOf(instruction.getId());
		if (approved) {
			instruction.upholdOffset("이의 검토 후 상계 유지" + comment);
			this.auditLog.record(new AuditRecord(Role.FINANCE.name(), "OFFSET_UPHELD", SUBJECT, id,
					"대리점 이의를 검토하고 상계 %,d원을 유지%s".formatted(instruction.getOffset().krw(), comment),
					Provenance.MOCK_REPLY, instruction.getCorrelationId()));
			return;
		}
		long cancelled = instruction.getOffset().krw();
		instruction.cancelOffset("이의 인정, 상계 취소 후 전액 지급" + comment);
		this.auditLog.record(new AuditRecord(Role.FINANCE.name(), "OFFSET_CANCELLED", SUBJECT, id,
				"대리점 이의를 인정해 상계 %,d원을 취소하고 %,d원 전액 지급, 미수금은 되살려 별도 회수%s".formatted(cancelled,
						instruction.getAmount().krw(), comment),
				Provenance.MOCK_REPLY, instruction.getCorrelationId()));
		outcome.reversals.add(new ReceivableOffsetsReversed.Reversal(reference(instruction),
				"회계·자금팀이 대리점 이의를 인정함" + comment, instruction.getCorrelationId()));
		routeToPayee(instruction, today, deposits, outcome);
	}

	/**
	 * The agency answered a request about a payment. For an account confirmation, a confirmed or
	 * newly named account releases the hold and no answer keeps it and hands the matter to audit,
	 * since closing this round does not end the duty to follow up. For an offset notice, a dispute
	 * sends a full offset back to finance.
	 */
	@ApplicationModuleListener
	void on(ClarificationClosed closed) {
		if (!SUBJECT.equals(closed.subjectType())) {
			return;
		}
		if (ClarificationKind.OFFSET_NOTICE.name().equals(closed.kind())) {
			onOffsetNoticeClosed(closed);
			return;
		}
		if (!ClarificationKind.ACCOUNT_CONFIRMATION.name().equals(closed.kind())) {
			return;
		}
		PaymentInstruction instruction = this.instructions.findById(Long.valueOf(closed.subjectId())).orElseThrow();
		if (instruction.getStatus() != PaymentStatus.HELD) {
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "PAYMENT_RELEASE_IGNORED", SUBJECT, closed.subjectId(),
					"이미 %s 상태라 계좌 확인 회신을 반영하지 않음".formatted(instruction.getStatus()), Provenance.MOCK_REPLY,
					instruction.getCorrelationId()));
			return;
		}
		ClarificationOutcome outcome = ClarificationOutcome.valueOf(closed.outcome());
		if (outcome == ClarificationOutcome.NO_REPLY) {
			instruction.extendHold("확인 요청 #%d 기한 내 무응답".formatted(closed.clarificationId()));
			this.auditLog.refer(new AuditReferral.Referral(SUBJECT, closed.subjectId(),
					"%s 판매장려금 %,d원 지급 계좌 미확인".formatted(instruction.getAgencyId(), instruction.getNetAmount().krw()),
					"계좌 확인 요청 #%d 에 기한 내 회신이 없어 지급 보류를 유지함".formatted(closed.clarificationId()),
					Role.SYSTEM.name(), instruction.getCorrelationId()));
			return;
		}
		String account = outcome == ClarificationOutcome.ACCOUNT_CHANGED ? closed.replyAccount()
				: instruction.getPayeeAccount();
		instruction.release(account);
		this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "PAYMENT_RELEASED", SUBJECT, closed.subjectId(),
				"계좌 확인 요청 #%d 회신(%s)에 따라 보류 해제, %s 계좌로 지급".formatted(closed.clarificationId(),
						outcome.label(), account),
				Provenance.MOCK_REPLY, instruction.getCorrelationId()));
		this.events.publishEvent(new PaymentBatchCreated(List.of(instruction.getId())));
	}

	private void onOffsetNoticeClosed(ClarificationClosed closed) {
		PaymentInstruction instruction = this.instructions.findById(Long.valueOf(closed.subjectId())).orElseThrow();
		ClarificationOutcome outcome = ClarificationOutcome.valueOf(closed.outcome());
		if (outcome != ClarificationOutcome.OFFSET_DISPUTED) {
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "OFFSET_CONFIRMED", SUBJECT, closed.subjectId(),
					"상계 통지 #%d: %s — 상계 %,d원 확정".formatted(closed.clarificationId(), outcome.label(),
							instruction.getOffset().krw()),
					Provenance.MOCK_REPLY, instruction.getCorrelationId()));
			return;
		}
		if (instruction.getStatus() != PaymentStatus.OFFSET_SETTLED) {
			// Part of the rebate is already on its way to the bank; undoing the offset needs a manual settlement.
			this.auditLog.refer(new AuditReferral.Referral(SUBJECT, closed.subjectId(),
					"%s 상계 이의 (부분 지급 진행 중)".formatted(instruction.getAgencyId()),
					"상계 통지 #%d 에 이의: %s. 잔여 %,d원이 이미 %s 상태라 자동으로 되돌리지 않고 수동 정산 대상으로 넘김".formatted(
							closed.clarificationId(), closed.replyText(), instruction.getNetAmount().krw(),
							instruction.getStatus()),
					Role.SYSTEM.name(), instruction.getCorrelationId()));
			return;
		}
		instruction.dispute(closed.replyText());
		this.auditLog.record(new AuditRecord("EXTERNAL:AGENCY", "OFFSET_DISPUTED", SUBJECT, closed.subjectId(),
				"상계 통지 #%d 에 이의: %s — 회계·자금팀에 재검토 상신".formatted(closed.clarificationId(), closed.replyText()),
				Provenance.MOCK_REPLY, instruction.getCorrelationId()));
		this.events.publishEvent(new ApprovalRequestsSubmitted(List.of(offsetRequest(instruction, closed.replyText()))));
	}

	/**
	 * The agency paid in, so less may be overdue. Offsets finance has not decided yet are proposed
	 * again for the new balance, or dropped when nothing is overdue long enough any more.
	 */
	@ApplicationModuleListener
	void on(OverdueBalanceChanged changed) {
		List<PaymentInstruction> waiting = this.instructions
			.findByAgencyIdAndStatusInOrderByIdAsc(changed.agencyId(), List.of(PaymentStatus.OFFSET_REVIEW))
			.stream()
			.filter((instruction) -> instruction.getOffsetRound() == 1)
			.toList();
		if (waiting.isEmpty()) {
			return;
		}
		LocalDate today = this.clock.today();
		List<Deposit> deposits = this.payeeAccounts.depositsUpTo(today);
		Outcome outcome = new Outcome();
		for (PaymentInstruction instruction : waiting) {
			String id = String.valueOf(instruction.getId());
			Optional<OverdueOffsetPolicy.Proposal> proposal = this.offsetPolicy.proposalFor(instruction.getAgencyId(),
					instruction.getAmount().krw());
			long before = instruction.getOffset().krw();
			if (proposal.isPresent() && proposal.get().offsetKrw() == before) {
				continue;
			}
			String reason = "입금 반영으로 %d일 초과 연체 잔액이 바뀜 (%s)".formatted(this.offsetPolicy.overdueDaysOver(),
					changed.cause());
			if (proposal.isEmpty()) {
				outcome.withdrawals.add(new ApprovalRequestsWithdrawn.Withdrawal(OFFSET_SUBJECT, id, reason,
						instruction.getCorrelationId()));
				instruction.waiveOffset("입금으로 상계 대상 연체가 해소되어 전액 지급");
				this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "OFFSET_DROPPED", SUBJECT, id,
						"상계 제안 %,d원 철회, 전액 %,d원 지급: %s".formatted(before, instruction.getAmount().krw(), reason),
						Provenance.MOCK_REPLY, instruction.getCorrelationId()));
				routeToPayee(instruction, today, deposits, outcome);
			}
			else {
				instruction.reviseOffset(proposal.get().offsetKrw(), proposal.get().basis());
				this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "OFFSET_REVISED", SUBJECT, id,
						"상계 제안 %,d원 → %,d원으로 다시 상신: %s".formatted(before, proposal.get().offsetKrw(), reason),
						Provenance.MOCK_REPLY, instruction.getCorrelationId()));
				// The new request replaces the pending one in the approval module.
				outcome.approvals.add(offsetRequest(instruction, null));
			}
		}
		outcome.publish();
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
					instruction.getPayeeAccount(), instruction.getNetAmount().krw()))
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
				paid.add(paid(instruction));
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

	/** Whether the offset extension is switched on, and the overdue threshold it uses. */
	public OffsetSettings offsetSettings() {
		return new OffsetSettings(this.offsetPolicy.enabled(), this.offsetPolicy.overdueDaysOver());
	}

	/**
	 * What would be offset if this amount were paid to the agency now, whether or not the extension
	 * is switched on. Used to compare the month with and without the extension.
	 */
	@Transactional(readOnly = true)
	public OffsetPreview previewOffset(String agencyId, long amountKrw) {
		return this.offsetPolicy.proposalFor(agencyId, amountKrw)
			.map((proposal) -> new OffsetPreview(agencyId, amountKrw, proposal.offsetKrw(), proposal.overdueKrw(),
					proposal.basis()))
			.orElse(new OffsetPreview(agencyId, amountKrw, 0, 0, null));
	}

	private void routeToPayee(PaymentInstruction instruction, LocalDate today, List<Deposit> deposits,
			Outcome outcome) {
		PayeeAccountCheck.Result payee = this.payeeAccounts.check(instruction.getAgencyId(), today, deposits);
		instruction.routeToPayee(payee.payeeAccount(), payee.holdReason());
		recordRouted(instruction, outcome);
	}

	private void recordRouted(PaymentInstruction instruction, Outcome outcome) {
		boolean held = instruction.getStatus() == PaymentStatus.HELD;
		this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), held ? "PAYMENT_HELD" : "PAYMENT_INSTRUCTED", SUBJECT,
				String.valueOf(instruction.getId()),
				held ? "%s %s 건 %,d원 지급 보류: %s (판단 규칙은 교육용 가정)".formatted(instruction.getReferenceType(),
						instruction.getReferenceId(), instruction.getNetAmount().krw(), instruction.getHoldReason())
						: "%s %s 건 %,d원을 %s 계좌 %s 로 지급하도록 지시 생성".formatted(instruction.getReferenceType(),
								instruction.getReferenceId(), instruction.getNetAmount().krw(), instruction.getAgencyId(),
								instruction.getPayeeAccount()),
				held ? Provenance.ASSUMPTION : Provenance.CALCULATED, instruction.getCorrelationId()));
		if (held) {
			outcome.confirmations.add(new ClarificationsRequested.Request(ClarificationKind.ACCOUNT_CONFIRMATION.name(),
					SUBJECT, String.valueOf(instruction.getId()), instruction.getAgencyId(),
					"%s 판매장려금 %,d원 지급 계좌 확인".formatted(instruction.getAgencyId(), instruction.getNetAmount().krw()),
					("판매장려금 %s 건 %,d원을 지급하기 전에 지급받을 계좌를 확인해 주십시오.%n%s%n"
							+ "등록 계좌가 맞으면 확인, 다른 계좌로 받으려면 그 계좌와 근거 서류(통장 사본 등)를 회신해 주십시오.")
						.formatted(instruction.getReferenceId(), instruction.getNetAmount().krw(),
								instruction.getHoldReason()),
					instruction.getCorrelationId()));
		}
		else {
			outcome.toSend.add(instruction.getId());
		}
	}

	private ApprovalRequestsSubmitted.Request offsetRequest(PaymentInstruction instruction, String dispute) {
		boolean disputed = dispute != null;
		String title = (disputed ? "[이의 제기·재검토] " : "") + "%s 판매장려금 %,d원 중 %,d원 미수금 상계".formatted(
				instruction.getAgencyId(), instruction.getAmount().krw(), instruction.getOffset().krw());
		String rationale = String.join("\n",
				"- 판매장려금 %s %,d원 (원본 지급표)".formatted(instruction.getReferenceId(), instruction.getAmount().krw()),
				"- 같은 대리점의 %s (원본 미수금 연령표, 이후 입금·상계 반영은 계산 결과)".formatted(instruction.getOffsetBasis()),
				"- 상계 제안액 %,d원 = 장려금과 연체 잔액 중 작은 금액, 잔여 지급 %,d원 (계산 결과)".formatted(
						instruction.getOffset().krw(), instruction.getNetAmount().krw()),
				"- 근거 [교육용 가정]: 대리점 계약서의 '이행기 지난 물품대금과 대등액 상계' 조항, 대상은 %d일 초과 연체분".formatted(
						this.offsetPolicy.overdueDaysOver()),
				"- 장려금을 몰수하는 것이 아님. 상계하면 같은 금액의 미수금이 함께 소멸함. 계약 근거 없는 장려금 삭감·미지급은 대리점법상"
						+ " 불이익 제공(공정위 2024-04 가구 3사 시정명령)",
				"- 관련자 그룹이어도 다른 법인의 미수금과는 상계하지 않음 (상계는 같은 당사자 사이 채권만, 민법 제492조)",
				disputed ? "- 대리점 이의 (모의 회신): " + dispute : "- 결정 뒤 대리점에 상계 통지, 이의가 오면 다시 회계·자금팀이 판단",
				disputed ? "- 승인 = 상계 유지 / 반려 = 상계 취소 후 전액 지급, 미수금 되살림"
						: "- 승인 = 상계 결정 / 반려 = 전액 지급 후 미수금 별도 회수");
		return new ApprovalRequestsSubmitted.Request(OFFSET_SUBJECT, String.valueOf(instruction.getId()),
				Role.FINANCE.name(), title, rationale, instruction.getOffset().krw(), instruction.getCorrelationId());
	}

	private ClarificationsRequested.Request offsetNotice(PaymentInstruction instruction) {
		return new ClarificationsRequested.Request(ClarificationKind.OFFSET_NOTICE.name(), SUBJECT,
				String.valueOf(instruction.getId()), instruction.getAgencyId(),
				"%s 판매장려금 %,d원 상계 통지".formatted(instruction.getAgencyId(), instruction.getOffset().krw()),
				("판매장려금 %s 건 %,d원 중 %,d원을 귀사의 %d일 넘게 연체된 물품대금과 대등액으로 상계했습니다(잔여 지급 %,d원).%n"
						+ "상계 대상: %s%n상계한 만큼 물품대금 채무도 소멸합니다. 상계 내역에 이의가 있으면 기한 안에 사유와 근거"
						+ "(반품·정산 미반영 내역 등)를 회신해 주십시오. 회신이 없으면 이의가 없는 것으로 봅니다.")
					.formatted(instruction.getReferenceId(), instruction.getAmount().krw(), instruction.getOffset().krw(),
							this.offsetPolicy.overdueDaysOver(), instruction.getNetAmount().krw(),
							instruction.getOffsetBasis()),
				instruction.getCorrelationId());
	}

	private static PaymentsCompleted.Paid paid(PaymentInstruction instruction) {
		return new PaymentsCompleted.Paid(instruction.getId(), instruction.getReferenceType(),
				instruction.getReferenceId(), instruction.getCorrelationId());
	}

	private static String reference(PaymentInstruction instruction) {
		return SUBJECT + "#" + instruction.getId();
	}

	/**
	 * @param enabled whether rebates of agencies with long-overdue receivables go to finance first
	 * @param overdueDaysOver receivables overdue by more days than this can be offset
	 */
	public record OffsetSettings(boolean enabled, int overdueDaysOver) {
	}

	/**
	 * @param agencyId the agency
	 * @param amountKrw the rebate
	 * @param offsetKrw what would be set off now
	 * @param overdueKrw the agency's balance overdue beyond the threshold now
	 * @param basis the invoices behind it, {@code null} when nothing would be offset
	 */
	public record OffsetPreview(String agencyId, long amountKrw, long offsetKrw, long overdueKrw, String basis) {
	}

	/** What one batch step produced, published as one event per kind. */
	private final class Outcome {

		final List<Long> toSend = new ArrayList<>();
		final List<ClarificationsRequested.Request> confirmations = new ArrayList<>();
		final List<ApprovalRequestsSubmitted.Request> approvals = new ArrayList<>();
		final List<ApprovalRequestsWithdrawn.Withdrawal> withdrawals = new ArrayList<>();
		final List<ReceivableOffsetsRequested.Offset> offsets = new ArrayList<>();
		final List<ReceivableOffsetsReversed.Reversal> reversals = new ArrayList<>();
		final List<PaymentsCompleted.Paid> completed = new ArrayList<>();
		final List<ClarificationsRequested.Request> notices = new ArrayList<>();

		void publish() {
			publishIfAny(this.withdrawals, ApprovalRequestsWithdrawn::new);
			publishIfAny(this.approvals, ApprovalRequestsSubmitted::new);
			publishIfAny(this.offsets, ReceivableOffsetsRequested::new);
			publishIfAny(this.reversals, ReceivableOffsetsReversed::new);
			publishIfAny(this.completed, PaymentsCompleted::new);
			publishIfAny(this.toSend, PaymentBatchCreated::new);
			List<ClarificationsRequested.Request> requests = new ArrayList<>(this.confirmations);
			requests.addAll(this.notices);
			publishIfAny(requests, ClarificationsRequested::new);
		}

		private <T> void publishIfAny(List<T> items, Function<List<T>, Object> event) {
			if (!items.isEmpty()) {
				PaymentService.this.events.publishEvent(event.apply(items));
			}
		}
	}
}
