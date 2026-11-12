package com.oneroof.receivables;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.intake.AgencyDepositReported;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records what reduces an agency's receivables after the aging report: offsets finance decided and
 * deposits the agency made. Amounts are applied to the most overdue invoice first.
 */
@Component
class ReceivableAdjustments {

	private static final String SUBJECT = "Receivable";

	private final ReceivableRepository receivables;
	private final ReceivableAdjustmentRepository adjustments;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	ReceivableAdjustments(ReceivableRepository receivables, ReceivableAdjustmentRepository adjustments,
			ApplicationEventPublisher events, AuditLog auditLog, BusinessClock clock) {
		this.receivables = receivables;
		this.adjustments = adjustments;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void on(ReceivableOffsetsRequested requested) {
		for (ReceivableOffsetsRequested.Offset offset : requested.offsets()) {
			if (this.adjustments.existsByKindAndReference(AdjustmentKind.OFFSET, offset.reference())) {
				continue;
			}
			List<ReceivableAdjustment> applied = apply(offset.agencyId(), offset.amountKrw(), AdjustmentKind.OFFSET,
					offset.reference(), Provenance.CALCULATED, offset.correlationId(),
					(item) -> item.agingDays() > offset.overdueDaysOver());
			long total = applied.stream().mapToLong(ReceivableAdjustment::getAmountKrw).sum();
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "RECEIVABLE_OFFSET", SUBJECT, offset.agencyId(),
					"%s 미수금 %,d원을 장려금과 상계 (%s): %s%s".formatted(offset.agencyId(), total, offset.reference(),
							describe(applied), total < offset.amountKrw()
									? " — 남은 연체 잔액이 부족해 %,d원은 반영하지 못함".formatted(offset.amountKrw() - total) : ""),
					Provenance.CALCULATED, offset.correlationId()));
		}
	}

	@ApplicationModuleListener
	void on(ReceivableOffsetsReversed reversed) {
		for (ReceivableOffsetsReversed.Reversal reversal : reversed.reversals()) {
			String reference = reversal.reference() + "/취소";
			if (this.adjustments.existsByKindAndReference(AdjustmentKind.OFFSET, reference)) {
				continue;
			}
			List<ReceivableAdjustment> original = this.adjustments.findByKindAndReference(AdjustmentKind.OFFSET,
					reversal.reference());
			if (original.isEmpty()) {
				continue;
			}
			List<ReceivableAdjustment> undone = this.adjustments.saveAll(original.stream()
				.map((adjustment) -> new ReceivableAdjustment(adjustment.getArId(), adjustment.getAgencyId(),
						AdjustmentKind.OFFSET, -adjustment.getAmountKrw(), reference, Provenance.CALCULATED,
						this.clock.today(), reversal.correlationId()))
				.toList());
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "RECEIVABLE_OFFSET_REVERSED", SUBJECT,
					original.get(0).getAgencyId(), "%s 상계 취소로 미수금 %,d원을 되살림: %s".formatted(reversal.reference(),
							-undone.stream().mapToLong(ReceivableAdjustment::getAmountKrw).sum(), reversal.reason()),
					Provenance.CALCULATED, reversal.correlationId()));
		}
	}

	@ApplicationModuleListener
	void on(AgencyDepositReported reported) {
		if (this.adjustments.existsByKindAndReference(AdjustmentKind.DEPOSIT, reported.eventId())) {
			return;
		}
		List<ReceivableAdjustment> applied = apply(reported.agencyId(), reported.amountKrw(), AdjustmentKind.DEPOSIT,
				reported.eventId(), Provenance.MOCK_REPLY, reported.eventId(), (item) -> true);
		long total = applied.stream().mapToLong(ReceivableAdjustment::getAmountKrw).sum();
		String cause = "%s 입금 %,d원(모의) 반영: %s".formatted(reported.agencyId(), total, describe(applied));
		this.auditLog.record(new AuditRecord(reported.reportedBy(), "RECEIVABLE_DEPOSIT", SUBJECT, reported.agencyId(),
				cause, Provenance.MOCK_REPLY, reported.eventId()));
		this.events.publishEvent(new OverdueBalanceChanged(reported.agencyId(), total, cause, reported.eventId()));
	}

	private List<ReceivableAdjustment> apply(String agencyId, long amountKrw, AdjustmentKind kind, String reference,
			Provenance provenance, String correlationId, Predicate<OpenItem> eligible) {
		List<OpenItem> items = ReceivablesLedger
			.open(this.receivables.findByAgencyIdOrderByAgingDaysDescArIdAsc(agencyId),
					this.adjustments.findByAgencyId(agencyId))
			.stream()
			.filter(eligible)
			.toList();
		List<ReceivableAdjustment> created = new ArrayList<>();
		long left = amountKrw;
		for (OpenItem item : items) {
			if (left == 0) {
				break;
			}
			long part = Math.min(left, item.outstandingKrw());
			created.add(new ReceivableAdjustment(item.arId(), agencyId, kind, part, reference, provenance,
					this.clock.today(), correlationId));
			left -= part;
		}
		return this.adjustments.saveAll(created);
	}

	private static String describe(List<ReceivableAdjustment> applied) {
		return applied.isEmpty() ? "반영할 미수금 없음"
				: String.join(", ", applied.stream()
					.map((adjustment) -> "%s %,d원".formatted(adjustment.getArId(), adjustment.getAmountKrw()))
					.toList());
	}
}
