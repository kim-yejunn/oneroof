package com.oneroof.audit;

import java.util.List;

import com.oneroof.shared.BusinessClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads the audit trail. Entries join the caller's transaction, so a state change and
 * its audit entry are committed together or not at all.
 */
@Service
public class AuditLog {

	private final AuditEntryRepository entries;
	private final AuditReferralRepository referrals;
	private final BusinessClock clock;

	AuditLog(AuditEntryRepository entries, AuditReferralRepository referrals, BusinessClock clock) {
		this.entries = entries;
		this.referrals = referrals;
		this.clock = clock;
	}

	/** Hands a matter to audit and records that it was handed over, in the caller's transaction. */
	@Transactional
	public AuditReferral refer(AuditReferral.Referral referral) {
		AuditReferral saved = this.referrals.save(new AuditReferral(referral, this.clock.today()));
		record(new AuditRecord(referral.referredBy(), "AUDIT_REFERRED", referral.subjectType(), referral.subjectId(),
				"감사 이관 #%d: %s — %s (부정 확정 아님, 후속 확인 대상)".formatted(saved.getId(), referral.title(),
						referral.reason()),
				com.oneroof.shared.Provenance.CALCULATED, referral.correlationId()));
		return saved;
	}

	@Transactional(readOnly = true)
	public List<AuditReferral> referrals() {
		return this.referrals.findAllByOrderByIdAsc();
	}

	@Transactional
	public AuditEntry record(AuditRecord record) {
		return this.entries.save(new AuditEntry(record, this.clock.recordedAt(), this.clock.today()));
	}

	@Transactional(readOnly = true)
	public List<AuditEntry> historyOf(String subjectType, String subjectId) {
		return this.entries.findBySubjectTypeAndSubjectIdOrderByIdAsc(subjectType, subjectId);
	}
}
