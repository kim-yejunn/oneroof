package com.oneroof.platform;

import java.util.List;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.shared.OriginalDataImporter;
import com.oneroof.shared.OriginalDataImporter.ImportResult;
import com.oneroof.shared.Provenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads the original data room records on startup when the database is empty. AXHub databases
 * start empty, so the first start after a deployment performs the load and later starts skip it.
 * Every load is written to the audit log with the checksum of the file that was read.
 */
@Component
class OriginalDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(OriginalDataSeeder.class);

	private final List<OriginalDataImporter> importers;
	private final AuditLog auditLog;

	OriginalDataSeeder(List<OriginalDataImporter> importers, AuditLog auditLog) {
		this.importers = importers;
		this.auditLog = auditLog;
	}

	@Override
	public void run(ApplicationArguments args) {
		for (OriginalDataImporter importer : this.importers) {
			for (ImportResult result : importer.importIfEmpty()) {
				if (result.alreadyPresent()) {
					log.info("Original data already present, skipped: {}", result.file());
					continue;
				}
				log.info("Loaded {} rows from {}", result.rows(), result.file());
				this.auditLog.record(new AuditRecord("SYSTEM", "ORIGINAL_DATA_LOADED", "OriginalFile", result.file(),
						"%d rows loaded, sha256=%s".formatted(result.rows(), result.sha256()), Provenance.ORIGINAL,
						null));
			}
		}
	}
}
