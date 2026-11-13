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
 * than from the original files (the schema history, the limit policy) are kept.
 *
 * <p>The app has no sign-in, so the reset needs a key known only to the presenter
 * ({@code oneroof.demo.reset-key}); without a configured key it is off. Emptying and reloading run
 * in one transaction: if loading fails, nothing was emptied, and the table locks taken by TRUNCATE
 * make concurrent writers wait until the reset is complete. The original files
 * themselves are never touched. Meant for rehearsing and recording the demo.
 */
@Service
public class DemoReset {

	/** Filled by Flyway migrations, not by the importers; emptying them would lose the data for good. */
	private static final List<String> KEPT = List.of("flyway_schema_history", "rebate_limit_policy");

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final OriginalDataSeeder seeder;
	private final AuditLog auditLog;
	private final String key;

	DemoReset(JdbcTemplate jdbc, TransactionTemplate transactions, OriginalDataSeeder seeder, AuditLog auditLog,
			@Value("${oneroof.demo.reset-key:}") String key) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.seeder = seeder;
		this.auditLog = auditLog;
		this.key = key == null ? "" : key.trim();
	}

	/**
	 * @param presentedKey the key sent with the request
	 * @return the number of tables emptied
	 * @throws SecurityException when the reset is off here or the key does not match
	 */
	public int reset(String presentedKey, String requestedBy) {
		if (this.key.isEmpty()) {
			throw new SecurityException("이 환경에는 시연 초기화 키가 설정되지 않아 초기화가 꺼져 있음");
		}
		if (presentedKey == null || !java.security.MessageDigest.isEqual(this.key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				presentedKey.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
			throw new SecurityException("시연 초기화 키가 맞지 않음");
		}
		List<String> tables = this.jdbc
			.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = current_schema() ORDER BY tablename",
					String.class)
			.stream()
			.filter((table) -> !KEPT.contains(table))
			.toList();
		// The screens keep polling while the reset runs, and their queries can lock the same tables in
		// another order. Postgres then aborts one side; when that is the reset, it simply runs again.
		for (int attempt = 1;; attempt++) {
			try {
				truncateAndReload(tables, requestedBy);
				return tables.size();
			}
			catch (org.springframework.dao.ConcurrencyFailureException ex) {
				if (attempt == 3) {
					throw ex;
				}
			}
		}
	}

	private void truncateAndReload(List<String> tables, String requestedBy) {
		this.transactions.executeWithoutResult((status) -> {
			this.jdbc.execute("TRUNCATE " + String.join(", ", tables.stream().map((table) -> "\"" + table + "\"").toList())
					+ " RESTART IDENTITY CASCADE");
			this.seeder.seed();
			this.auditLog.record(new AuditRecord(requestedBy, "DEMO_RESET", "Demo", "reset",
					"시연 초기화: 테이블 %d개를 비우고 원본을 다시 적재함 (원본 파일은 그대로)".formatted(tables.size()),
					Provenance.ORIGINAL, null));
		});
	}
}
