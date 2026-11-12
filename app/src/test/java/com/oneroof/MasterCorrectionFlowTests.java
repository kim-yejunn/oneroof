package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.oneroof.approval.ApprovalRequest;
import com.oneroof.approval.ApprovalService;
import com.oneroof.intake.IntakeService;
import com.oneroof.masterdata.MasterCorrectionService;
import com.oneroof.payment.PaymentInstruction;
import com.oneroof.payment.PaymentService;
import com.oneroof.rebate.RebateReview;
import com.oneroof.rebate.RebateSettlementService;
import com.oneroof.rebate.ReviewStatus;
import com.oneroof.relatedparty.RelatedPartyService;
import com.oneroof.shared.Money;
import com.oneroof.shared.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The changed-condition case: during the June settlement the sales support team reports that D-041
 * moved into D-034's premises at the start of the year. D-034 and D-041 already share a
 * representative, so the shared address makes them a related group. The undecided June review is
 * replaced by a group review for the executive, and the months already settled under the sales
 * support team's authority that the group exceeded become retrospective findings.
 */
@SpringBootTest(properties = "oneroof.offset.enabled=false")
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MasterCorrectionFlowTests {

	private static final YearMonth JUNE = YearMonth.of(2026, 6);
	private static final String D034_ADDRESS = "대전시 중앙로 260";

	@Autowired
	private IntakeService intake;

	@Autowired
	private ApprovalService approvals;

	@Autowired
	private RebateSettlementService settlement;

	@Autowired
	private PaymentService payments;

	@Autowired
	private MasterCorrectionService corrections;

	@Autowired
	private RelatedPartyService relatedParties;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void regroupsUndecidedReviewsAndFindsSettledMonthsThatNeededTheExecutive() {
		this.intake.receiveSettlementBatch("EVT-SETTLEMENT-2026-06", JUNE, Role.SALES_SUPPORT.name());
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.SALES_SUPPORT)).hasSize(37));

		// D-034's own review is approved before the correction arrives; D-041's is still undecided.
		ApprovalRequest d034 = pendingAbout(Role.SALES_SUPPORT, "D-034 ");
		this.approvals.decide(d034.getId(), Role.SALES_SUPPORT, "영업지원 담당", true, "한도 이내 전결");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("payment_instruction WHERE agency_id = 'D-034'")).isEqualTo(1));
		ApprovalRequest d041 = pendingAbout(Role.SALES_SUPPORT, "D-041 ");

		// A correction that changes nothing is refused before it is reported.
		assertThat(this.corrections.problemWith("D-041", "ADDRESS", "고양시 중앙로 128", LocalDate.of(2026, 1, 1)))
			.hasValueSatisfying((problem) -> assertThat(problem).contains("같은 값"));

		this.intake.receiveMasterChange("EVT-MASTER-D041", "D-041", "ADDRESS", D034_ADDRESS, LocalDate.of(2026, 1, 1),
				"2025-12 사업장 이전 신고서 접수, 마스터 미반영 확인", Role.SALES_SUPPORT.name());

		// 1. The master keeps its history: before the correction took effect the two were not related.
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("master_change")).isEqualTo(1));
		assertThat(this.relatedParties.analysisAsOf(LocalDate.of(2025, 12, 31)).groupOf("D-041")).isEmpty();
		assertThat(this.relatedParties.analysisAsOf(LocalDate.of(2026, 7, 1)).groupOf("D-041"))
			.hasValueSatisfying((group) -> assertThat(group.members()).containsExactly("D-034", "D-041"));

		// 2. The undecided D-041 review is replaced and its request withdrawn; the new group review
		// carries the group total, which is over the limit, to the executive.
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.SALES_EXECUTIVE)
				.stream()
				.filter((request) -> request.getTitle().contains("RPG-D-034") && request.getSubjectType().equals("RebateReview")))
				.singleElement()
				.satisfies((request) -> {
					assertThat(request.getAmount()).isEqualTo(Money.ofKrw(2_255_709));
					assertThat(request.getRationale()).contains("[재분류]").contains("마스터 정정 #");
				}));
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(status("approval_request", d041.getId())).isEqualTo("WITHDRAWN"));
		assertThat(this.approvals.pendingFor(Role.SALES_SUPPORT)).hasSize(35);
		RebateReview replaced = review(Long.parseLong(d041.getSubjectId()));
		assertThat(replaced.getStatus()).isEqualTo(ReviewStatus.SUPERSEDED);
		RebateReview groupReview = this.settlement.reviewsOf(JUNE)
			.stream()
			.filter((review) -> review.getUnitKey().equals("RPG-D-034"))
			.findFirst()
			.orElseThrow();
		assertThat(this.settlement.claimsOf(groupReview.getId())).singleElement()
			.satisfies((claim) -> assertThat(claim.getAgencyId()).isEqualTo("D-041"));
		// The decided D-034 review stands.
		assertThat(review(Long.parseLong(d034.getSubjectId())).getStatus()).isEqualTo(ReviewStatus.APPROVED);

		// 3. Months settled under the sales support team that the group exceeded: Jan–Mar in the
		// source (2,276,288 / 2,218,474 / 2,007,205) and June's D-034, approved here before the
		// correction. April (1,600,007) and May (1,851,017) stayed within the limit.
		assertThat(this.jdbc.queryForList("SELECT period_ym FROM retrospective_finding ORDER BY period_ym", String.class))
			.containsExactly("2026-01", "2026-02", "2026-03", "2026-06");
		assertThat(this.jdbc.queryForObject("SELECT total_krw FROM retrospective_finding WHERE period_ym = '2026-01'",
				Long.class)).isEqualTo(2_276_288L);
		List<ApprovalRequest> findings = this.approvals.pendingFor(Role.SALES_EXECUTIVE)
			.stream()
			.filter((request) -> request.getSubjectType().equals("RetrospectiveFinding"))
			.toList();
		assertThat(findings).hasSize(4);

		// 4. The executive confirms January after the fact and declines February, which goes to audit.
		ApprovalRequest january = findings.stream().filter((r) -> r.getTitle().startsWith("2026-01")).findFirst().orElseThrow();
		ApprovalRequest february = findings.stream().filter((r) -> r.getTitle().startsWith("2026-02")).findFirst().orElseThrow();
		this.approvals.decide(january.getId(), Role.SALES_EXECUTIVE, "영업임원", true, "사업장 이전 전 계약 조건 확인");
		this.approvals.decide(february.getId(), Role.SALES_EXECUTIVE, "영업임원", false, "증빙 부족");
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
			assertThat(findingStatus("2026-01")).isEqualTo("CONFIRMED");
			assertThat(findingStatus("2026-02")).isEqualTo("REFERRED_TO_AUDIT");
		});
		assertThat(count("audit_referral WHERE subject_type = 'RetrospectiveFinding'")).isEqualTo(1);

		// 5. The executive rejects the new group: the agencies are asked to explain; they do not
		// answer in time, so the review goes to audit and the claim is held.
		ApprovalRequest groupRequest = this.approvals.pendingFor(Role.SALES_EXECUTIVE)
			.stream()
			.filter((request) -> request.getSubjectId().equals(String.valueOf(groupReview.getId())))
			.findFirst()
			.orElseThrow();
		this.approvals.decide(groupRequest.getId(), Role.SALES_EXECUTIVE, "영업임원", false, "공유 주소 사유 확인 필요");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("clarification_request WHERE kind = 'RELATION_EXPLANATION' "
					+ "AND agency_ids = 'D-034,D-041'")).isEqualTo(1));
		assertThat(review(groupReview.getId()).getStatus()).isEqualTo(ReviewStatus.CLARIFICATION_REQUESTED);
		long clarification = this.jdbc.queryForObject(
				"SELECT id FROM clarification_request WHERE kind = 'RELATION_EXPLANATION'", Long.class);
		this.intake.receiveClarificationReply("EVT-CLARIFY-" + clarification, clarification, "NO_REPLY", "", null);
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(review(groupReview.getId()).getStatus())
				.isEqualTo(ReviewStatus.REFERRED_TO_AUDIT));
		assertThat(count("rebate_claim WHERE agency_id = 'D-041' AND period_ym = '2026-06' "
				+ "AND settlement_status = 'HELD_FOR_AUDIT'")).isEqualTo(1);

		// 6. The original group is approved; D-024's payment is held, and the agency names a
		// different account. The reply corrects the master (as a mock reply) and releases the payment
		// to that account. The account takes effect today, so no past month becomes a finding.
		ApprovalRequest original = this.approvals.pendingFor(Role.SALES_EXECUTIVE)
			.stream()
			.filter((request) -> request.getTitle().contains("RPG-D-011"))
			.findFirst()
			.orElseThrow();
		this.approvals.decide(original.getId(), Role.SALES_EXECUTIVE, "영업임원", true, "합산 확인");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("clarification_request WHERE kind = 'ACCOUNT_CONFIRMATION'")).isEqualTo(1));
		long confirmation = this.jdbc.queryForObject(
				"SELECT id FROM clarification_request WHERE kind = 'ACCOUNT_CONFIRMATION'", Long.class);
		this.intake.receiveClarificationReply("EVT-CLARIFY-" + confirmation, confirmation, "ACCOUNT_CHANGED",
				"정산계좌를 국민은행 계좌로 바꿔 주십시오. 통장 사본 첨부", "국민 412-27-556310");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.payments.awaitingBankReply()).extracting(PaymentInstruction::getAgencyId)
				.contains("D-024"));
		assertThat(this.jdbc.queryForObject("SELECT payee_account FROM payment_instruction WHERE agency_id = 'D-024'",
				String.class)).isEqualTo("국민 412-27-556310");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("master_change WHERE agency_id = 'D-024' AND provenance = 'MOCK_REPLY'"))
				.isEqualTo(1));
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("audit_log WHERE action = 'MASTER_CHANGE_ASSESSED'")).isEqualTo(2));
		assertThat(count("retrospective_finding")).isEqualTo(4);

		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("event_publication WHERE completion_date IS NULL")).isZero());
	}

	private ApprovalRequest pendingAbout(Role role, String titlePart) {
		return this.approvals.pendingFor(role)
			.stream()
			.filter((request) -> request.getTitle().contains(titlePart))
			.findFirst()
			.orElseThrow();
	}

	private RebateReview review(long id) {
		return this.settlement.reviewsOf(JUNE).stream().filter((review) -> review.getId() == id).findFirst().orElseThrow();
	}

	private String status(String table, long id) {
		return this.jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
	}

	private String findingStatus(String period) {
		return this.jdbc.queryForObject("SELECT status FROM retrospective_finding WHERE period_ym = ?", String.class,
				period);
	}

	private long count(String tableAndFilter) {
		return this.jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Long.class);
	}
}
