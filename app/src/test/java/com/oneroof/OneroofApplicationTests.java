package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.oneroof.audit.AuditEntry;
import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.shared.Provenance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OneroofApplicationTests {

	@Autowired
	private AuditLog auditLog;

	@Test
	void migratesSchemaAndRecordsAuditEntriesOnTheBusinessDate() {
		this.auditLog.record(new AuditRecord("SYSTEM", "CONTEXT_STARTED", "Application", "oneroof",
				"Schema migrated and validated", Provenance.CALCULATED, "test-correlation"));

		List<AuditEntry> history = this.auditLog.historyOf("Application", "oneroof");

		assertThat(history).hasSize(1);
		assertThat(history.get(0).getBusinessDate()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(history.get(0).getProvenance()).isEqualTo(Provenance.CALCULATED);
	}
}
