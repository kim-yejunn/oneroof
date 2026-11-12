package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.YearMonth;

import com.jayway.jsonpath.JsonPath;
import com.oneroof.approval.ApprovalRequest;
import com.oneroof.approval.ApprovalService;
import com.oneroof.clarification.ClarificationRequest;
import com.oneroof.clarification.ClarificationService;
import com.oneroof.intake.IntakeService;
import com.oneroof.approval.AuthorityViolationException;
import com.oneroof.receivables.OpenItem;
import com.oneroof.receivables.ReceivablesLedger;
import com.oneroof.shared.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The P7 extension: before paying June, finance decides whether to set rebates off against
 * receivables overdue by more than 90 days. The figures come from the original aging report: 21 of
 * the 40 June agencies are that far overdue, and in every case the overdue balance exceeds the rebate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OffsetFlowTests {

	private static final YearMonth JUNE = YearMonth.of(2026, 6);

	private final HttpClient http = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private IntakeService intake;

	@Autowired
	private ApprovalService approvals;

	@Autowired
	private ClarificationService clarifications;

	@Autowired
	private ReceivablesLedger ledger;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void offsetsRebatesAgainstLongOverdueReceivablesOnlyWhenFinanceDecides() throws Exception {
		// Before anything happens, the comparison shows what the extension changes in June.
		String comparison = get("/api/offsets/comparison?period=2026-06");
		assertThat(JsonPath.<Number>read(comparison, "$.totalKrw").longValue()).isEqualTo(37_159_812L);
		assertThat(JsonPath.<Number>read(comparison, "$.proposedOffsetKrw").longValue()).isEqualTo(20_806_770L);
		assertThat(JsonPath.<Number>read(comparison, "$.proposedCashKrw").longValue()).isEqualTo(16_353_042L);
		assertThat(JsonPath.<Integer>read(comparison, "$.offsetAgencyCount")).isEqualTo(21);
		assertThat(JsonPath.<Number>read(comparison, "$.originalOffsetKrw").longValue()).isEqualTo(20_806_770L);

		// 1. The sales support team clears its 37 routine reviews. 18 of those agencies are more than
		// 90 days overdue: nothing is paid to them until finance decides; the other 19 go to the bank.
		this.intake.receiveSettlementBatch("EVT-SETTLEMENT-2026-06", JUNE, Role.SALES_SUPPORT.name());
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.SALES_SUPPORT)).hasSize(37));
		this.approvals.decideAll(this.approvals.pendingFor(Role.SALES_SUPPORT).stream().map(ApprovalRequest::getId).toList(),
				Role.SALES_SUPPORT, "영업지원팀", true, "한도 이내 전결");
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.FINANCE)).hasSize(18));
		assertThat(count("payment_instruction WHERE status = 'OFFSET_REVIEW'")).isEqualTo(18);
		assertThat(count("payment_instruction WHERE status = 'SENT'")).isEqualTo(19);
		assertThat(sum("offset_krw", "status = 'OFFSET_REVIEW'")).isEqualTo(16_918_673L);
		// Only finance decides an offset; the sales support team has no authority over it.
		long anyOffset = this.approvals.pendingFor(Role.FINANCE).get(0).getId();
		assertThatThrownBy(() -> this.approvals.decide(anyOffset, Role.SALES_SUPPORT, "영업지원팀", true, ""))
			.isInstanceOf(AuthorityViolationException.class);
		assertThat(this.approvals.pendingFor(Role.FINANCE).get(0).getRationale()).contains("교육용 가정")
			.contains("몰수하는 것이 아님")
			.contains("민법 제492조");

		// 2. Condition change: before finance decides, D-001 pays its overdue invoice in full and D-034
		// pays most of its. D-001's proposal is dropped and it is paid in full; D-034's is proposed again.
		this.intake.receiveAgencyDeposit("EVT-DEPOSIT-D-001", "D-001", 7_487_427L, "기업 198-22-851114",
				Role.FINANCE.name());
		this.intake.receiveAgencyDeposit("EVT-DEPOSIT-D-034", "D-034", 8_000_000L, "국민 551-20-118734",
				Role.FINANCE.name());
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(instruction("D-001", "status")).isEqualTo("SENT");
			assertThat(this.jdbc.queryForObject(
					"SELECT offset_krw FROM payment_instruction WHERE agency_id = 'D-034'", Long.class))
				.isEqualTo(1_038_360L);
			assertThat(this.approvals.pendingFor(Role.FINANCE)).hasSize(17);
		});
		assertThat(this.ledger.overdueBeyond("D-001", 90)).isEmpty();
		assertThat(this.approvals.pendingFor(Role.FINANCE))
			.filteredOn((request) -> request.getTitle().startsWith("D-034"))
			.singleElement()
			.satisfies((request) -> assertThat(request.getAmount().krw()).isEqualTo(1_038_360L));

		// 3. Finance sets off all 17. Sixteen are covered in full and settle without a bank transfer;
		// D-034's remaining 315,297원 goes to the bank. Receivables fall by the same amounts.
		this.approvals.decideAll(this.approvals.pendingFor(Role.FINANCE).stream().map(ApprovalRequest::getId).toList(),
				Role.FINANCE, "회계·자금팀", true, "계약 조항에 따라 상계");
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(count("payment_instruction WHERE status = 'OFFSET_SETTLED'")).isEqualTo(16);
			assertThat(instruction("D-034", "status")).isEqualTo("SENT");
			assertThat(count("receivable_adjustment WHERE kind = 'OFFSET'")).isGreaterThanOrEqualTo(17);
		});
		assertThat(sum("offset_krw", "status IN ('OFFSET_SETTLED', 'SENT')")).isEqualTo(15_614_444L);
		assertThat(this.jdbc.queryForObject(
				"SELECT amount_krw - offset_krw FROM payment_instruction WHERE agency_id = 'D-034'", Long.class))
			.isEqualTo(315_297L);
		assertThat(this.ledger.overdueBeyond("D-034", 90)).isEmpty();
		assertThat(this.ledger.overdueBeyond("D-012", 90).stream().mapToLong(OpenItem::outstandingKrw).sum())
			.isEqualTo(8_517_694L - 547_490L);
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(count("rebate_claim WHERE period_ym = '2026-06' AND settlement_status = 'PAID'"))
				.isEqualTo(16));
		// Each agency is told what was set off and can object.
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(count("clarification_request WHERE kind = 'OFFSET_NOTICE'")).isEqualTo(17));

		// 4. Exception: D-012 disputes the offset. A dispute without a reason is refused; with one,
		// finance looks again, accepts it, the offset is cancelled and the full rebate is paid.
		long notice = notice("D-012").getId();
		assertThat(this.clarifications.problemWith(notice, "OFFSET_DISPUTED", "", null)).isPresent();
		this.intake.receiveClarificationReply("EVT-CLARIFY-" + notice, notice, "OFFSET_DISPUTED",
				"5월 반품 2건(1,200,000원) 정산이 미수금에 반영되지 않음. 반품 확인서 첨부", null);
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.FINANCE)).singleElement()
				.satisfies((request) -> {
					assertThat(request.getTitle()).startsWith("[이의 제기·재검토] D-012");
					assertThat(request.getRationale()).contains("반품 확인서 첨부");
				}));
		assertThat(instruction("D-012", "status")).isEqualTo("OFFSET_DISPUTED");
		this.approvals.decide(this.approvals.pendingFor(Role.FINANCE).get(0).getId(), Role.FINANCE, "회계·자금팀", false,
				"반품 미반영 확인, 상계 취소");
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			assertThat(instruction("D-012", "status")).isEqualTo("SENT");
			assertThat(this.ledger.overdueBeyond("D-012", 90).stream().mapToLong(OpenItem::outstandingKrw).sum())
				.isEqualTo(8_517_694L);
		});
		assertThat(this.jdbc.queryForObject("SELECT offset_krw FROM payment_instruction WHERE agency_id = 'D-012'",
				Long.class)).isZero();

		// 5. D-002 does not answer; its offset stands.
		long silent = notice("D-002").getId();
		this.intake.receiveClarificationReply("EVT-CLARIFY-" + silent, silent, "NO_REPLY", "", null);
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(count("audit_log WHERE action = 'OFFSET_CONFIRMED'")).isEqualTo(1));
		assertThat(instruction("D-002", "status")).isEqualTo("OFFSET_SETTLED");

		// The original-basis comparison does not move; what finance actually set off does.
		String after = get("/api/offsets/comparison?period=2026-06");
		assertThat(JsonPath.<Number>read(after, "$.originalOffsetKrw").longValue()).isEqualTo(20_806_770L);
		assertThat(JsonPath.<Number>read(after, "$.settledOffsetKrw").longValue()).isEqualTo(15_614_444L - 547_490L);

		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(count("event_publication WHERE completion_date IS NULL")).isZero());
	}

	private ClarificationRequest notice(String agencyId) {
		long instructionId = this.jdbc.queryForObject("SELECT id FROM payment_instruction WHERE agency_id = ?", Long.class,
				agencyId);
		return this.clarifications.requestsAbout("PaymentInstruction", String.valueOf(instructionId))
			.stream()
			.filter((request) -> request.getKind().name().equals("OFFSET_NOTICE"))
			.findFirst()
			.orElseThrow();
	}

	private String instruction(String agencyId, String column) {
		return this.jdbc.queryForObject("SELECT " + column + " FROM payment_instruction WHERE agency_id = ?",
				String.class, agencyId);
	}

	private long count(String tableAndFilter) {
		return this.jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Long.class);
	}

	private long sum(String column, String filter) {
		return this.jdbc.queryForObject("SELECT coalesce(sum(" + column + "), 0) FROM payment_instruction WHERE " + filter,
				Long.class);
	}

	private String get(String path) throws Exception {
		return this.http
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).GET().build(),
					HttpResponse.BodyHandlers.ofString())
			.body();
	}
}
