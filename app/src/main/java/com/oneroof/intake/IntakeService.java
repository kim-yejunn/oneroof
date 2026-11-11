package com.oneroof.intake;

import java.time.YearMonth;

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

	@Transactional
	public Receipt receivePaymentResult(String eventId, long instructionId, boolean succeeded, String bankReference) {
		String summary = "지급 지시 #%d 은행 회신: %s (%s)".formatted(instructionId, succeeded ? "이체 완료" : "이체 실패",
				bankReference);
		if (!accept(eventId, "PAYMENT_RESULT", summary, "EXTERNAL:BANK", Provenance.MOCK_REPLY)) {
			return new Receipt(eventId, false);
		}
		this.events.publishEvent(new PaymentResultReceived(eventId, instructionId, succeeded, bankReference));
		return new Receipt(eventId, true);
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
