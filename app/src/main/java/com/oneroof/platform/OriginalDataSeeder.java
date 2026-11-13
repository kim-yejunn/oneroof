package com.oneroof.platform;

import java.util.List;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.shared.OriginalDataImporter;
import com.oneroof.shared.OriginalDataImporter.ImportResult;
import com.oneroof.shared.Provenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Loads the original data room records on startup when the database is empty. AXHub databases
 * start empty, so the first start after a deployment performs the load and later starts skip it.
 * Every load is written to the audit log with the checksum of the file that was read.
 *
 * <p>Runs as a lifecycle step in an early phase, before the embedded web server starts, so neither
 * the health check nor any API request is answered while the data is still loading. (As an
 * application runner it ran after the server was up, and a settlement submitted in that window
 * found nothing to settle and used up the month's event id.)
 */
@Component
class OriginalDataSeeder implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(OriginalDataSeeder.class);

	private final List<OriginalDataImporter> importers;
	private final AuditLog auditLog;

	OriginalDataSeeder(List<OriginalDataImporter> importers, AuditLog auditLog) {
		this.importers = importers;
		this.auditLog = auditLog;
	}

	private volatile boolean running;

	@Override
	public void start() {
		seed();
		this.running = true;
	}

	@Override
	public void stop() {
		this.running = false;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	/** Well before the web server, which starts in one of the last phases. */
	@Override
	public int getPhase() {
		return 0;
	}

	void seed() {
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
