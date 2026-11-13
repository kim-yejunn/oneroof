package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.YearMonth;

import com.oneroof.approval.ApprovalRequest;
import com.oneroof.approval.ApprovalService;
import com.oneroof.intake.IntakeService;
import com.oneroof.platform.DemoReset;
import com.oneroof.shared.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Two paths the other flows leave out: an agency that never confirms its payee account, and the
 * demo reset that puts everything back to the original data.
 */
@SpringBootTest(properties = "oneroof.offset.enabled=false")
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HoldNoReplyAndResetTests {

	@Autowired
	private IntakeService intake;

	@Autowired
	private ApprovalService approvals;

	@Autowired
	private DemoReset demoReset;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void keepsAnUnconfirmedPaymentHeldAndReferredThenResetsTheDemo() {
		this.intake.receiveSettlementBatch("EVT-SETTLEMENT-2026-06", YearMonth.of(2026, 6), Role.SALES_SUPPORT.name());
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.SALES_EXECUTIVE)).hasSize(1));
		ApprovalRequest group = this.approvals.pendingFor(Role.SALES_EXECUTIVE).get(0);
		this.approvals.decide(group.getId(), Role.SALES_EXECUTIVE, "영업임원", true, "합산 확인");

		// D-024's deposits came from another account: held, and the agency never answers.
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("payment_instruction WHERE status = 'HELD'")).isEqualTo(1));
		// The confirmation request is created by a later background step than the hold.
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("clarification_request WHERE kind = 'ACCOUNT_CONFIRMATION'")).isEqualTo(1));
		long clarification = this.jdbc.queryForObject(
				"SELECT id FROM clarification_request WHERE kind = 'ACCOUNT_CONFIRMATION'", Long.class);
		this.intake.receiveClarificationReply("EVT-CLARIFY-" + clarification, clarification, "NO_REPLY", "", null);
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("audit_referral WHERE subject_type = 'PaymentInstruction'")).isEqualTo(1));
		assertThat(this.jdbc.queryForObject("SELECT status FROM payment_instruction WHERE agency_id = 'D-024'",
				String.class)).isEqualTo("HELD");
		assertThat(this.jdbc.queryForObject("SELECT hold_reason FROM payment_instruction WHERE agency_id = 'D-024'",
				String.class)).contains("기한 내 무응답");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("event_publication WHERE completion_date IS NULL")).isZero());

		// The demo reset empties every table and loads the originals again.
		assertThat(this.demoReset.reset(Role.SALES_SUPPORT.name())).isGreaterThan(10);
		assertThat(count("payment_instruction")).isZero();
		assertThat(count("approval_request")).isZero();
		assertThat(count("rebate_claim WHERE period_ym = '2026-06' AND settlement_status = 'PENDING'")).isEqualTo(40);
		assertThat(count("agency")).isEqualTo(50);
		assertThat(count("receivable")).isEqualTo(146);
		assertThat(count("audit_log WHERE action = 'DEMO_RESET'")).isEqualTo(1);

		// The same month can be submitted again after the reset.
		assertThat(this.intake.receiveSettlementBatch("EVT-SETTLEMENT-2026-06", YearMonth.of(2026, 6),
				Role.SALES_SUPPORT.name()).accepted()).isTrue();
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.SALES_SUPPORT)).hasSize(37));
	}

	private long count(String tableAndFilter) {
		return this.jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Long.class);
	}
}
