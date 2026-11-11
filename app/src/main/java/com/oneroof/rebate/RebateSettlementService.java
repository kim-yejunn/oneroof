package com.oneroof.rebate;

import java.time.YearMonth;
import java.util.List;

import com.oneroof.approval.ApprovalDecided;
import com.oneroof.approval.ApprovalRequestSubmitted;
import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.intake.SettlementBatchReceived;
import com.oneroof.payment.PaymentCompleted;
import com.oneroof.payment.PaymentRequested;
import com.oneroof.relatedparty.RelatedPartyAnalysis;
import com.oneroof.relatedparty.RelatedPartyService;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a month's rebates through settlement: groups the unpaid claims into limit units, asks the
 * role with authority to decide each unit, and after approval requests payment. The system
 * prepares and routes; it never approves.
 */
@Service
public class RebateSettlementService {

	static final String CLAIM_REFERENCE = "RebateClaim";

	private final SettlementBatchRepository batches;
	private final RebateReviewRepository reviews;
	private final RebateClaimRepository claims;
	private final RebateLimitPolicyRepository policies;
	private final RelatedPartyService relatedParties;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	RebateSettlementService(SettlementBatchRepository batches, RebateReviewRepository reviews,
			RebateClaimRepository claims, RebateLimitPolicyRepository policies, RelatedPartyService relatedParties,
			ApplicationEventPublisher events, AuditLog auditLog, BusinessClock clock) {
		this.batches = batches;
		this.reviews = reviews;
		this.claims = claims;
		this.policies = policies;
		this.relatedParties = relatedParties;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void on(SettlementBatchReceived received) {
		YearMonth period = YearMonth.parse(received.period());
		String correlationId = received.eventId();
		if (this.batches.existsByPeriodYm(period.toString())) {
			audit(Role.SYSTEM, "SETTLEMENT_ALREADY_OPEN", "SettlementBatch", period.toString(),
					period + " 정산은 이미 접수되어 다시 열지 않음", correlationId);
			return;
		}
		RebateLimitPolicy policy = this.policies
			.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(period.atEndOfMonth())
			.orElseThrow(() -> new IllegalStateException("No rebate limit policy in force for " + period));
		RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(this.clock.today());
		List<ExposureUnit> units = RebateExposureCalculator
			.calculate(period, this.claims.findByPeriodYmOrderByAgencyIdAsc(period.toString()), analysis, policy)
			.stream()
			.filter((unit) -> unit.claims().stream().anyMatch(RebateSettlementService::isPending))
			.toList();
		if (units.isEmpty()) {
			audit(Role.SYSTEM, "NOTHING_TO_SETTLE", "SettlementBatch", period.toString(),
					period + " 에는 미지급 장려금이 없어 정산을 열지 않음", correlationId);
			return;
		}

		SettlementBatch batch = this.batches.save(new SettlementBatch(period, correlationId, this.clock.today()));
		for (ExposureUnit unit : units) {
			String title = ReviewRationale.title(unit);
			String rationale = ReviewRationale.text(unit, analysis.groupOf(unit.agencyIds().get(0)).orElse(null),
					policy.getSourceRef());
			RebateReview review = this.reviews.save(new RebateReview(batch.getId(), unit, title, rationale, correlationId));
			unit.claims().stream().filter(RebateSettlementService::isPending)
				.forEach((claim) -> claim.submitForReview(review.getId()));
			audit(Role.SYSTEM, "REVIEW_OPENED", RebateReview.SUBJECT_TYPE, String.valueOf(review.getId()),
					"%s → %s 상신".formatted(title, unit.requiredApprover().label()), correlationId);
			this.events.publishEvent(new ApprovalRequestSubmitted(RebateReview.SUBJECT_TYPE,
					String.valueOf(review.getId()), unit.requiredApprover().name(), title, rationale, unit.total().krw(),
					correlationId));
		}
		audit(Role.SYSTEM, "SETTLEMENT_OPENED", "SettlementBatch", period.toString(),
				"%s 정산 접수: 검토 %d건 (임원 상신 %d건, 영업지원팀 전결 %d건)".formatted(period, units.size(),
						units.stream().filter((unit) -> unit.requiredApprover() == Role.SALES_EXECUTIVE).count(),
						units.stream().filter((unit) -> unit.requiredApprover() == Role.SALES_SUPPORT).count()),
				correlationId);
	}

	@ApplicationModuleListener
	void on(ApprovalDecided decided) {
		if (!RebateReview.SUBJECT_TYPE.equals(decided.subjectType())) {
			return;
		}
		RebateReview review = this.reviews.findById(Long.valueOf(decided.subjectId())).orElseThrow();
		List<RebateClaim> reviewClaims = this.claims.findByReviewIdOrderByAgencyIdAsc(review.getId());
		if (!decided.approved()) {
			review.reject();
			reviewClaims.forEach(RebateClaim::reject);
			audit(Role.SYSTEM, "REVIEW_REJECTED", RebateReview.SUBJECT_TYPE, decided.subjectId(),
					"승인 반려에 따라 %d건 지급 중단".formatted(reviewClaims.size()), decided.correlationId());
			return;
		}
		review.approve();
		for (RebateClaim claim : reviewClaims) {
			claim.requestPayment();
			this.events.publishEvent(new PaymentRequested(CLAIM_REFERENCE, claim.getRebateId(), claim.getAgencyId(),
					claim.getAmount().krw(), decided.correlationId()));
		}
		audit(Role.SYSTEM, "PAYMENT_REQUESTED", RebateReview.SUBJECT_TYPE, decided.subjectId(),
				"승인에 따라 %d건 지급 요청".formatted(reviewClaims.size()), decided.correlationId());
	}

	@ApplicationModuleListener
	void on(PaymentCompleted completed) {
		if (!CLAIM_REFERENCE.equals(completed.referenceType())) {
			return;
		}
		RebateClaim claim = this.claims.findById(completed.referenceId()).orElseThrow();
		RebateReview review = this.reviews.findWithLockById(claim.getReviewId()).orElseThrow();
		claim.markPaid();
		boolean allPaid = this.claims.findByReviewIdOrderByAgencyIdAsc(review.getId())
			.stream()
			.allMatch((each) -> each.getSettlementStatus() == ClaimStatus.PAID);
		if (allPaid) {
			review.markPaid();
			audit(Role.SYSTEM, "REVIEW_PAID", RebateReview.SUBJECT_TYPE, String.valueOf(review.getId()),
					"검토에 속한 장려금이 모두 지급 완료됨", completed.correlationId());
		}
	}

	@Transactional(readOnly = true)
	public List<RebateReview> reviewsOf(YearMonth period) {
		return this.reviews.findByPeriodYmOrderByIdAsc(period.toString());
	}

	@Transactional(readOnly = true)
	public List<RebateClaim> claimsOf(long reviewId) {
		return this.claims.findByReviewIdOrderByAgencyIdAsc(reviewId);
	}

	private static boolean isPending(RebateClaim claim) {
		return claim.getSettlementStatus() == ClaimStatus.PENDING;
	}

	private void audit(Role actor, String action, String subjectType, String subjectId, String detail,
			String correlationId) {
		this.auditLog.record(new AuditRecord(actor.name(), action, subjectType, subjectId, detail, Provenance.CALCULATED,
				correlationId));
	}
}
