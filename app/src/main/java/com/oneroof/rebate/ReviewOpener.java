package com.oneroof.rebate;

import java.util.ArrayList;
import java.util.List;

import com.oneroof.approval.ApprovalRequestsSubmitted;
import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.relatedparty.RelatedPartyAnalysis;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.stereotype.Component;

/**
 * Opens one review per limit unit, attaches the unit's unpaid claims to it, and prepares the
 * request to the role with authority. Used when a month is first submitted and when a master
 * correction changes the units of a month already under review.
 */
@Component
class ReviewOpener {

	private final RebateReviewRepository reviews;
	private final AuditLog auditLog;

	ReviewOpener(RebateReviewRepository reviews, AuditLog auditLog) {
		this.reviews = reviews;
		this.auditLog = auditLog;
	}

	/**
	 * @param note added to each rationale to say why the review exists, or {@code null}
	 * @return the approval requests to publish, one per review, in the order of {@code units}
	 */
	List<ApprovalRequestsSubmitted.Request> open(long batchId, List<ExposureUnit> units, RelatedPartyAnalysis analysis,
			RebateLimitPolicy policy, String note, String correlationId) {
		List<ApprovalRequestsSubmitted.Request> requests = new ArrayList<>(units.size());
		for (ExposureUnit unit : units) {
			String title = ReviewRationale.title(unit);
			String rationale = ReviewRationale.text(unit, analysis.groupOf(unit.agencyIds().get(0)).orElse(null),
					policy.getSourceRef());
			if (note != null) {
				rationale = note + "\n" + rationale;
			}
			RebateReview review = this.reviews.save(new RebateReview(batchId, unit, title, rationale, correlationId));
			unit.claims()
				.stream()
				.filter((claim) -> claim.getSettlementStatus() == ClaimStatus.PENDING)
				.forEach((claim) -> claim.submitForReview(review.getId()));
			this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), "REVIEW_OPENED", RebateReview.SUBJECT_TYPE,
					String.valueOf(review.getId()), "%s → %s 상신".formatted(title, unit.requiredApprover().label()),
					Provenance.CALCULATED, correlationId));
			requests.add(new ApprovalRequestsSubmitted.Request(RebateReview.SUBJECT_TYPE, String.valueOf(review.getId()),
					unit.requiredApprover().name(), title, rationale, unit.total().krw(), correlationId));
		}
		return requests;
	}
}
