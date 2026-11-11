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
	private final BusinessClock clock;

	AuditLog(AuditEntryRepository entries, BusinessClock clock) {
		this.entries = entries;
		this.clock = clock;
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
