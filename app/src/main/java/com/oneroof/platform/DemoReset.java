package com.oneroof.platform;

import java.util.List;

import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.shared.Provenance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Puts the demonstration back to the state right after the original data was loaded: every table
 * is emptied and the originals are loaded again. Tables whose rows come from migrations rather
 * than from the original files (the schema history, the limit policy) are kept. The original files
 * themselves are never touched. Meant for rehearsing and recording the demo; switched off with
 * {@code oneroof.demo.reset-enabled=false}.
 */
@Service
public class DemoReset {

	/** Filled by Flyway migrations, not by the importers; emptying them would lose the data for good. */
	private static final List<String> KEPT = List.of("flyway_schema_history", "rebate_limit_policy");

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final OriginalDataSeeder seeder;
	private final AuditLog auditLog;
	private final boolean enabled;

	DemoReset(JdbcTemplate jdbc, TransactionTemplate transactions, OriginalDataSeeder seeder, AuditLog auditLog,
			@Value("${oneroof.demo.reset-enabled:true}") boolean enabled) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.seeder = seeder;
		this.auditLog = auditLog;
		this.enabled = enabled;
	}

	public boolean enabled() {
		return this.enabled;
	}

	/** @return the number of tables emptied */
	public int reset(String requestedBy) {
		if (!this.enabled) {
			throw new IllegalStateException("이 환경에서는 시연 초기화가 꺼져 있음");
		}
		List<String> tables = this.jdbc
			.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = current_schema() ORDER BY tablename",
					String.class)
			.stream()
			.filter((table) -> !KEPT.contains(table))
			.toList();
		this.transactions.executeWithoutResult((status) -> this.jdbc.execute("TRUNCATE "
				+ String.join(", ", tables.stream().map((table) -> "\"" + table + "\"").toList())
				+ " RESTART IDENTITY CASCADE"));
		this.seeder.seed();
		this.auditLog.record(new AuditRecord(requestedBy, "DEMO_RESET", "Demo", "reset",
				"시연 초기화: 테이블 %d개를 비우고 원본을 다시 적재함 (원본 파일은 그대로)".formatted(tables.size()),
				Provenance.ORIGINAL, null));
		return tables.size();
	}
}
