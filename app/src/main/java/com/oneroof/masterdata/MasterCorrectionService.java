package com.oneroof.masterdata;

import java.time.LocalDate;
import java.util.Optional;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.clarification.ClarificationClosed;
import com.oneroof.clarification.ClarificationKind;
import com.oneroof.clarification.ClarificationOutcome;
import com.oneroof.intake.MasterChangeReported;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies corrections to the agency master. A correction never overwrites: the current value is
 * closed the day the new one takes effect, so earlier dates are still evaluated as registered then.
 */
@Service
public class MasterCorrectionService {

	private static final String SUBJECT = "Agency";

	private final AgencyRepository agencies;
	private final AgencyIdentityRepository identities;
	private final MasterChangeRepository changes;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	MasterCorrectionService(AgencyRepository agencies, AgencyIdentityRepository identities,
			MasterChangeRepository changes, ApplicationEventPublisher events, AuditLog auditLog, BusinessClock clock) {
		this.agencies = agencies;
		this.identities = identities;
		this.changes = changes;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	/** A change reported by a department. The new value is that department's claim until verified. */
	@ApplicationModuleListener
	void on(MasterChangeReported reported) {
		apply(reported.eventId(), reported.agencyId(), reported.kind(), reported.newValue(),
				LocalDate.parse(reported.effectiveFrom()), reported.reason(), reported.reportedBy(),
				Provenance.DEPARTMENT_CLAIM);
	}

	/** An agency named a different payee account in answer to a confirmation request. */
	@ApplicationModuleListener
	void on(ClarificationClosed closed) {
		if (!ClarificationKind.ACCOUNT_CONFIRMATION.name().equals(closed.kind())
				|| !ClarificationOutcome.ACCOUNT_CHANGED.name().equals(closed.outcome())) {
			return;
		}
		apply("CLARIFICATION-%d".formatted(closed.clarificationId()), closed.agencyIds(),
				IdentityKind.BANK_ACCOUNT.name(), closed.replyAccount(), this.clock.today(),
				"지급 계좌 확인 요청 #%d 에 대리점이 회신한 계좌".formatted(closed.clarificationId()), "EXTERNAL:AGENCY",
				Provenance.MOCK_REPLY);
	}

	/**
	 * Checks a correction before it is reported, so the console can refuse it at once. Returns the
	 * reason it cannot be applied, if any.
	 */
	@Transactional(readOnly = true)
	public Optional<String> problemWith(String agencyId, String kind, String newValue, LocalDate effectiveFrom) {
		if (agencyId == null || this.agencies.findById(agencyId).isEmpty()) {
			return Optional.of("없는 대리점: " + agencyId);
		}
		IdentityKind parsed;
		try {
			parsed = IdentityKind.valueOf(kind);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			return Optional.of("알 수 없는 식별정보 종류: " + kind);
		}
		if (newValue == null || newValue.isBlank()) {
			return Optional.of("새 값이 비어 있음");
		}
		if (effectiveFrom == null || effectiveFrom.isAfter(this.clock.today())) {
			return Optional.of("적용일은 기준일(%s) 이전이어야 함".formatted(this.clock.today()));
		}
		Optional<AgencyIdentity> current = this.identities.findByAgencyIdAndKindAndEffectiveToIsNull(agencyId, parsed);
		if (current.isEmpty()) {
			return Optional.of("%s 의 현재 %s 가 없음".formatted(agencyId, parsed.label()));
		}
		if (current.get().getValue().trim().equals(newValue.trim())) {
			return Optional.of("현재 등록된 %s 와 같은 값임".formatted(parsed.label()));
		}
		if (!effectiveFrom.isAfter(current.get().getEffectiveFrom())) {
			return Optional.of("적용일은 현재 값의 적용 시작일(%s) 이후여야 함".formatted(current.get().getEffectiveFrom()));
		}
		return Optional.empty();
	}

	private void apply(String eventId, String agencyId, String kind, String newValue, LocalDate effectiveFrom,
			String reason, String reportedBy, Provenance provenance) {
		if (this.changes.existsByEventId(eventId)) {
			return;
		}
		Optional<String> problem = problemWith(agencyId, kind, newValue, effectiveFrom);
		if (problem.isPresent()) {
			this.auditLog.record(new AuditRecord(reportedBy, "MASTER_CHANGE_REJECTED", SUBJECT, String.valueOf(agencyId),
					problem.get() + " — 정정을 반영하지 않음", provenance, eventId));
			return;
		}
		IdentityKind identityKind = IdentityKind.valueOf(kind);
		AgencyIdentity current = this.identities.findByAgencyIdAndKindAndEffectiveToIsNull(agencyId, identityKind)
			.orElseThrow();
		String value = newValue.trim();
		MasterChange change = this.changes.save(new MasterChange(eventId, agencyId, identityKind, current.getValue(),
				value, effectiveFrom, reason, reportedBy, provenance, this.clock.today()));
		current.closeOn(effectiveFrom);
		this.identities.save(new AgencyIdentity(agencyId, identityKind, value, effectiveFrom, provenance,
				"master_change#" + change.getId()));
		this.auditLog.record(new AuditRecord(reportedBy, "MASTER_CHANGED", SUBJECT, agencyId,
				"%s %s 정정: '%s' → '%s' (%s 부터, 출처 %s) — %s".formatted(agencyId, identityKind.label(),
						current.getValue(), value, effectiveFrom, provenance.label(), reason),
				provenance, eventId));
		this.events.publishEvent(new AgencyIdentityCorrected(change.getId(), agencyId, identityKind.name(),
				current.getValue(), value, effectiveFrom.toString(), reason, provenance.name(), eventId));
	}

	/** Every identifier row of one agency, closed ones included, to show its history. */
	@Transactional(readOnly = true)
	public java.util.List<AgencyIdentity> historyOf(String agencyId) {
		return this.identities.findByAgencyIdOrderByKindAscEffectiveFromAsc(agencyId);
	}
}
