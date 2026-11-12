package com.oneroof.intake;

import java.time.YearMonth;
import java.util.List;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single entry point for business events arriving from outside. An event is stored and
 * announced to the other modules in one transaction; an event id seen before is acknowledged
 * without doing anything, so senders can safely retry.
 */
@Service
public class IntakeService {

	private final InboxEventRepository inbox;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	IntakeService(InboxEventRepository inbox, ApplicationEventPublisher events, AuditLog auditLog,
			BusinessClock clock) {
		this.inbox = inbox;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@Transactional
	public Receipt receiveSettlementBatch(String eventId, YearMonth period, String submittedByRole) {
		if (!accept(eventId, "SETTLEMENT_BATCH", period + " 판매장려금 정산 배치", submittedByRole, Provenance.ORIGINAL)) {
			return new Receipt(eventId, false);
		}
		this.events.publishEvent(new SettlementBatchReceived(eventId, period.toString()));
		return new Receipt(eventId, true);
	}

	/**
	 * Accepts a bank reply covering one or many instructions. Only the reply itself is stored here,
	 * in one transaction; applying it to each instruction happens afterwards, so the sender gets an
	 * answer at once however large the reply is.
	 */
	@Transactional
	public Receipt receivePaymentResults(String eventId, List<PaymentResultsReceived.Result> results) {
		if (results.isEmpty()) {
			throw new IllegalArgumentException("은행 회신에 결과가 없음");
		}
		long succeeded = results.stream().filter(PaymentResultsReceived.Result::succeeded).count();
		String summary = results.size() == 1
				? "지급 지시 #%d 은행 회신: %s (%s)".formatted(results.get(0).instructionId(),
						results.get(0).succeeded() ? "이체 완료" : "이체 실패", results.get(0).bankReference())
				: "은행 일괄 회신 %d건: 이체 완료 %d, 이체 실패 %d".formatted(results.size(), succeeded,
						results.size() - succeeded);
		if (!accept(eventId, "PAYMENT_RESULT", summary, "EXTERNAL:BANK", Provenance.MOCK_REPLY)) {
			return new Receipt(eventId, false);
		}
		this.events.publishEvent(new PaymentResultsReceived(eventId, List.copyOf(results)));
		return new Receipt(eventId, true);
	}

	/** Accepts a report that an agency's registered identifier changed. Checking it is the master data module's job. */
	@Transactional
	public Receipt receiveMasterChange(String eventId, String agencyId, String kind, String newValue,
			java.time.LocalDate effectiveFrom, String reason, String reportedBy) {
		String summary = "%s %s 정정 신고: '%s' (%s 부터) — %s".formatted(agencyId, kind, newValue, effectiveFrom, reason);
		if (!accept(eventId, "MASTER_CHANGE", summary, reportedBy, Provenance.DEPARTMENT_CLAIM)) {
			return new Receipt(eventId, false);
		}
		this.events.publishEvent(new MasterChangeReported(eventId, agencyId, kind, newValue, effectiveFrom.toString(),
				reason, reportedBy));
		return new Receipt(eventId, true);
	}

	/** Accepts an agency's (simulated) answer to a clarification request, or notice that it never came. */
	@Transactional
	public Receipt receiveClarificationReply(String eventId, long clarificationId, String outcome, String replyText,
			String replyAccount) {
		String summary = "소명·확인 요청 #%d 회신: %s%s".formatted(clarificationId, outcome,
				replyText == null || replyText.isBlank() ? "" : " — " + replyText);
		if (!accept(eventId, "CLARIFICATION_REPLY", summary, "EXTERNAL:AGENCY", Provenance.MOCK_REPLY)) {
			return new Receipt(eventId, false);
		}
		this.events.publishEvent(new ClarificationReplyReceived(eventId, clarificationId, outcome,
				replyText == null ? "" : replyText, replyAccount));
		return new Receipt(eventId, true);
	}

	@Transactional
	public Receipt receivePaymentResult(String eventId, long instructionId, boolean succeeded, String bankReference) {
		return receivePaymentResults(eventId,
				List.of(new PaymentResultsReceived.Result(instructionId, succeeded, bankReference)));
	}

	private boolean accept(String eventId, String eventType, String summary, String actorRole, Provenance provenance) {
		if (this.inbox.existsById(eventId)) {
			this.auditLog.record(new AuditRecord(actorRole, "DUPLICATE_EVENT_IGNORED", "InboxEvent", eventId,
					"같은 사건 ID가 이미 접수되어 다시 처리하지 않음: " + summary, provenance, eventId));
			return false;
		}
		this.inbox.save(new InboxEvent(eventId, eventType, summary, this.clock.recordedAt(), this.clock.today()));
		this.auditLog.record(new AuditRecord(actorRole, "EVENT_RECEIVED", "InboxEvent", eventId, summary, provenance,
				eventId));
		return true;
	}
}
