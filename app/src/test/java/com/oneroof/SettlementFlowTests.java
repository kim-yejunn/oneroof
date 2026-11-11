package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;

import com.oneroof.approval.ApprovalRequest;
import com.oneroof.approval.ApprovalService;
import com.oneroof.approval.AuthorityViolationException;
import com.oneroof.intake.IntakeService;
import com.oneroof.intake.Receipt;
import com.oneroof.payment.PaymentInstruction;
import com.oneroof.payment.PaymentService;
import com.oneroof.rebate.RebateReview;
import com.oneroof.rebate.RebateSettlementService;
import com.oneroof.rebate.ReviewStatus;
import com.oneroof.shared.Money;
import com.oneroof.shared.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The normal case end to end on the original data: the June 2026 settlement is submitted, the
 * related group is routed to the sales executive and everything else to the sales support team,
 * approval creates payment instructions, and the bank's reply closes the loop.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SettlementFlowTests {

	private static final YearMonth JUNE = YearMonth.of(2026, 6);
	private static final String EVENT_ID = "EVT-SETTLEMENT-2026-06";

	@Autowired
	private IntakeService intake;

	@Autowired
	private ApprovalService approvals;

	@Autowired
	private RebateSettlementService settlement;

	@Autowired
	private PaymentService payments;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void settlesJuneFromSubmissionToConfirmedPayment() {
		// 1. Submission: one event opens 38 reviews; only the related group goes to the executive.
		Receipt receipt = this.intake.receiveSettlementBatch(EVENT_ID, JUNE, Role.SALES_SUPPORT.name());
		assertThat(receipt.accepted()).isTrue();

		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.approvals.pendingFor(Role.SALES_SUPPORT)).hasSize(37));
		List<ApprovalRequest> executiveQueue = this.approvals.pendingFor(Role.SALES_EXECUTIVE);
		assertThat(executiveQueue).singleElement().satisfies((request) -> {
			assertThat(request.getAmount()).isEqualTo(Money.ofKrw(3_888_097));
			assertThat(request.getTitle()).contains("RPG-D-011").contains("3개 대리점");
			assertThat(request.getRationale()).contains("D-011 ↔ D-024")
				.contains("실제 입금 계좌")
				.contains("부정을 확정하지 않음");
		});
		assertThat(this.settlement.reviewsOf(JUNE)).hasSize(38)
			.allMatch((review) -> review.getStatus() == ReviewStatus.AWAITING_APPROVAL);
		assertThat(count("rebate_claim WHERE period_ym = '2026-06' AND settlement_status = 'UNDER_APPROVAL'"))
			.isEqualTo(40);
		assertThat(count("payment_instruction")).isZero();

		// 2. The same event delivered again changes nothing.
		assertThat(this.intake.receiveSettlementBatch(EVENT_ID, JUNE, Role.SALES_SUPPORT.name()).accepted()).isFalse();
		assertThat(count("rebate_review")).isEqualTo(38);

		// 3. Authority is enforced: the sales support team cannot decide the executive's request.
		ApprovalRequest groupRequest = executiveQueue.get(0);
		assertThatThrownBy(
				() -> this.approvals.decide(groupRequest.getId(), Role.SALES_SUPPORT, "영업지원 담당", true, "전결 처리"))
			.isInstanceOf(AuthorityViolationException.class);
		assertThat(this.approvals.pendingFor(Role.SALES_EXECUTIVE)).hasSize(1);

		// 4. The executive approves: three instructions are created and handed to the (mock) bank.
		this.approvals.decide(groupRequest.getId(), Role.SALES_EXECUTIVE, "영업임원", true, "합산 금액 확인 후 승인");
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.payments.awaitingBankReply()).hasSize(3));
		List<PaymentInstruction> groupInstructions = this.payments.awaitingBankReply();
		assertThat(groupInstructions).extracting(PaymentInstruction::getAgencyId)
			.containsExactlyInAnyOrder("D-011", "D-024", "D-037");
		assertThat(groupInstructions.stream().mapToLong((instruction) -> instruction.getAmount().krw()).sum())
			.isEqualTo(3_888_097L);
		RebateReview groupReview = review(groupRequest);
		assertThat(groupReview.getStatus()).isEqualTo(ReviewStatus.APPROVED);

		// 5. The bank confirms each transfer; the review is paid once all three are confirmed.
		for (PaymentInstruction instruction : groupInstructions) {
			this.intake.receivePaymentResult("EVT-BANK-" + instruction.getId(), instruction.getId(), true,
					"BANK-TX-" + instruction.getId());
		}
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(review(groupRequest).getStatus()).isEqualTo(ReviewStatus.PAID));
		assertThat(count("rebate_claim WHERE review_id = " + groupReview.getId() + " AND settlement_status = 'PAID'"))
			.isEqualTo(3);

		// 6. The sales support team clears its queue in one action.
		List<Long> routine = this.approvals.pendingFor(Role.SALES_SUPPORT).stream().map(ApprovalRequest::getId).toList();
		this.approvals.decideAll(routine, Role.SALES_SUPPORT, "영업지원 담당", true, "한도 이내 전결");
		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(this.payments.awaitingBankReply()).hasSize(37));
		assertThat(count("rebate_claim WHERE period_ym = '2026-06' AND settlement_status = 'PAYMENT_REQUESTED'"))
			.isEqualTo(37);

		// 7. Every step left a trace tied to the submitting event, and no event is left undelivered.
		assertThat(count("audit_log WHERE correlation_id = '" + EVENT_ID + "' AND action = 'APPROVAL_GRANTED'"))
			.isEqualTo(38);
		assertThat(count("audit_log WHERE correlation_id = '" + EVENT_ID + "' AND action = 'PAYMENT_SENT'"))
			.isEqualTo(40);
		assertThat(count("audit_log WHERE action = 'DUPLICATE_EVENT_IGNORED'")).isEqualTo(1);
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(count("event_publication WHERE completion_date IS NULL")).isZero());
	}

	private RebateReview review(ApprovalRequest request) {
		long reviewId = Long.parseLong(request.getSubjectId());
		return this.settlement.reviewsOf(JUNE)
			.stream()
			.filter((review) -> review.getId() == reviewId)
			.findFirst()
			.orElseThrow();
	}

	private long count(String tableAndFilter) {
		return this.jdbc.queryForObject("SELECT count(*) FROM " + tableAndFilter, Long.class);
	}
}
