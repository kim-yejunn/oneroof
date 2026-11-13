package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.YearMonth;

import com.oneroof.approval.ApprovalRequest;
import com.oneroof.approval.ApprovalService;
import com.oneroof.clarification.ClarificationService;
import com.oneroof.intake.IntakeService;
import com.oneroof.rebate.RebateReview;
import com.oneroof.rebate.RebateSettlementService;
import com.oneroof.rebate.ReviewStatus;
import com.oneroof.shared.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The rejection case: the executive rejects the related group. Shared identifiers are grounds for
 * an explanation, not proof, so the group is not closed: its agencies are asked to explain, the
 * explanation goes back to the executive, and a second rejection hands the matter to audit with
 * the claims held rather than written off.
 */
@SpringBootTest(properties = "oneroof.offset.enabled=false")
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RejectionClarificationFlowTests {

	private static final YearMonth JUNE = YearMonth.of(2026, 6);

	@Autowired
	private IntakeService intake;

	@Autowired
	private ApprovalService approvals;

	@Autowired
	private RebateSettlementService settlement;

	@Autowired
	private ClarificationService clarifications;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void asksForAnExplanationBeforeASecondDecisionAndRefersToAuditAfterIt() {
		this.intake.receiveSettlementBatch("EVT-SETTLEMENT-2026-06", JUNE, Role.SALES_SUPPORT.name());
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.SALES_EXECUTIVE)).hasSize(1));
		ApprovalRequest first = this.approvals.pendingFor(Role.SALES_EXECUTIVE).get(0);
		long reviewId = Long.parseLong(first.getSubjectId());

		// 1. First rejection: the review waits for an explanation, the claims stay unpaid but open.
		this.approvals.decide(first.getId(), Role.SALES_EXECUTIVE, "영업임원", false, "주소·계좌 공유 사유 확인 필요");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.clarifications.requestsAbout(RebateReview.SUBJECT_TYPE,
					String.valueOf(reviewId)))
				.singleElement()
				.satisfies((request) -> {
					assertThat(request.getAgencyIds()).isEqualTo("D-011,D-024,D-037");
					assertThat(request.getQuestion()).contains("D-011 ↔ D-024")
						.contains("부정을 확정한 것이 아닙니다")
						.contains("주소·계좌 공유 사유 확인 필요");
					assertThat(request.getDueOn()).isEqualTo(request.getSentOn().plusDays(7));
				}));
		assertThat(review(reviewId).getStatus()).isEqualTo(ReviewStatus.CLARIFICATION_REQUESTED);
		assertThat(count("rebate_claim WHERE review_id = " + reviewId + " AND settlement_status = 'UNDER_APPROVAL'"))
			.isEqualTo(3);
		assertThat(count("payment_instruction")).isZero();

		// 2. A reply that does not fit the question is refused before it is accepted.
		long clarification = this.clarifications.requestsAbout(RebateReview.SUBJECT_TYPE, String.valueOf(reviewId))
			.get(0)
			.getId();
		assertThat(this.clarifications.problemWith(clarification, "ACCOUNT_CONFIRMED", null)).isPresent();

		// 3. The agencies explain; the review goes back to the executive with the answer attached.
		this.intake.receiveClarificationReply("EVT-CLARIFY-" + clarification, clarification, "EXPLAINED",
				"세 법인은 대표가 같은 별도 법인으로 성남 사무실을 함께 임차함. 임대차계약서·법인등기부 첨부", null);
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.SALES_EXECUTIVE)).singleElement()
				.satisfies((request) -> {
					assertThat(request.getTitle()).startsWith("[재상신·소명 첨부]");
					assertThat(request.getRationale()).contains("임대차계약서·법인등기부 첨부").contains("모의 회신");
				}));
		assertThat(review(reviewId).getStatus()).isEqualTo(ReviewStatus.AWAITING_APPROVAL);
		assertThat(review(reviewId).getRound()).isEqualTo(2);
		// The reply is also interpreted for the executive (rules here; no model is called in tests).
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.jdbc.queryForObject(
					"SELECT evidence FROM reply_interpretation WHERE clarification_id = ?", String.class, clarification))
				.contains("임대차계약서")
				.contains("법인등기부"));

		// 4. A second reply to the same request is acknowledged and ignored.
		assertThat(this.intake.receiveClarificationReply("EVT-CLARIFY-" + clarification, clarification, "NO_REPLY", "",
				null).accepted()).isFalse();

		// 5. The executive rejects again: audit takes over and the claims are held, not written off.
		ApprovalRequest second = this.approvals.pendingFor(Role.SALES_EXECUTIVE).get(0);
		this.approvals.decide(second.getId(), Role.SALES_EXECUTIVE, "영업임원", false, "증빙이 관계를 설명하지 못함");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(review(reviewId).getStatus()).isEqualTo(ReviewStatus.REFERRED_TO_AUDIT));
		assertThat(count("rebate_claim WHERE review_id = " + reviewId + " AND settlement_status = 'HELD_FOR_AUDIT'"))
			.isEqualTo(3);
		assertThat(this.jdbc.queryForObject("SELECT reason FROM audit_referral WHERE subject_type = 'RebateReview'",
				String.class)).contains("다시 반려").contains("증빙이 관계를 설명하지 못함");
		assertThat(count("payment_instruction")).isZero();

		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("event_publication WHERE completion_date IS NULL")).isZero());
	}

	private RebateReview review(long id) {
		return this.settlement.reviewsOf(JUNE).stream().filter((review) -> review.getId() == id).findFirst().orElseThrow();
	}

	private long count(String tableAndFilter) {
		return this.jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Long.class);
	}
}
