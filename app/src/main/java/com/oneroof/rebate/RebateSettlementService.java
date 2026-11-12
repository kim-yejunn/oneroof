package com.oneroof.rebate;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oneroof.approval.ApprovalRequestsSubmitted;
import com.oneroof.approval.ApprovalsDecided;
import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditReferral;
import com.oneroof.audit.AuditRecord;
import com.oneroof.clarification.ClarificationClosed;
import com.oneroof.clarification.ClarificationKind;
import com.oneroof.clarification.ClarificationOutcome;
import com.oneroof.clarification.ClarificationsRequested;
import com.oneroof.intake.SettlementBatchReceived;
import com.oneroof.payment.PaymentsCompleted;
import com.oneroof.payment.PaymentsRequested;
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
	private final RetrospectiveFindingRepository findings;
	private final ReviewOpener opener;
	private final RebateClaimRepository claims;
	private final RebateLimitPolicyRepository policies;
	private final RelatedPartyService relatedParties;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	RebateSettlementService(SettlementBatchRepository batches, RebateReviewRepository reviews,
			RetrospectiveFindingRepository findings, ReviewOpener opener, RebateClaimRepository claims, RebateLimitPolicyRepository policies, RelatedPartyService relatedParties,
			ApplicationEventPublisher events, AuditLog auditLog, BusinessClock clock) {
		this.batches = batches;
		this.reviews = reviews;
		this.findings = findings;
		this.opener = opener;
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
		List<ApprovalRequestsSubmitted.Request> approvalRequests = this.opener.open(batch.getId(), units, analysis, policy,
				null, correlationId);
		this.events.publishEvent(new ApprovalRequestsSubmitted(approvalRequests));
		audit(Role.SYSTEM, "SETTLEMENT_OPENED", "SettlementBatch", period.toString(),
				"%s 정산 접수: 검토 %d건 (임원 상신 %d건, 영업지원팀 전결 %d건)".formatted(period, units.size(),
						units.stream().filter((unit) -> unit.requiredApprover() == Role.SALES_EXECUTIVE).count(),
						units.stream().filter((unit) -> unit.requiredApprover() == Role.SALES_SUPPORT).count()),
				correlationId);
	}

	/**
	 * Applies one approval action to every subject it decided, and asks for all resulting payments
	 * at once. A rejected related group is not closed straight away: the relation is grounds for an
	 * explanation, not proof of wrongdoing, so the agencies are asked first. A second rejection, after
	 * the explanation, hands the review to audit.
	 */
	@ApplicationModuleListener
	void on(ApprovalsDecided decided) {
		List<PaymentsRequested.Payment> payments = new ArrayList<>();
		List<ClarificationsRequested.Request> clarifications = new ArrayList<>();
		for (ApprovalsDecided.Decision decision : decided.decisions()) {
			if (RetrospectiveFinding.SUBJECT_TYPE.equals(decision.subjectType())) {
				decideFinding(decision);
				continue;
			}
			if (!RebateReview.SUBJECT_TYPE.equals(decision.subjectType())) {
				continue;
			}
			RebateReview review = this.reviews.findById(Long.valueOf(decision.subjectId())).orElseThrow();
			if (review.getStatus() != ReviewStatus.AWAITING_APPROVAL) {
				audit(Role.SYSTEM, "DECISION_IGNORED", RebateReview.SUBJECT_TYPE, decision.subjectId(),
						"검토가 이미 %s 상태라 승인 요청 #%d 의 결정을 반영하지 않음".formatted(review.getStatus(),
								decision.requestId()),
						decision.correlationId());
				continue;
			}
			List<RebateClaim> reviewClaims = this.claims.findByReviewIdOrderByAgencyIdAsc(review.getId());
			if (!decision.approved()) {
				if (review.isRelatedGroup() && review.getRound() == 1) {
					review.requestClarification();
					clarifications.add(relationClarification(review, decision.comment()));
					audit(Role.SYSTEM, "CLARIFICATION_REQUESTED", RebateReview.SUBJECT_TYPE, decision.subjectId(),
							"관련자 그룹 반려 — 확정 반려 전에 %s 에 관계 소명을 요청하고 지급 대기 유지".formatted(
									String.join(", ", review.getMemberAgencyIds())),
							decision.correlationId());
				}
				else if (review.isRelatedGroup()) {
					referReviewToAudit(review, reviewClaims,
							"소명 회신을 첨부해 재상신했으나 %s이 다시 반려함%s".formatted(
									Role.valueOf(decision.decidedByRole()).label(),
									decision.comment().isBlank() ? "" : ": " + decision.comment()),
							decision.correlationId());
				}
				else {
					review.reject();
					reviewClaims.forEach(RebateClaim::reject);
					audit(Role.SYSTEM, "REVIEW_REJECTED", RebateReview.SUBJECT_TYPE, decision.subjectId(),
							"승인 반려에 따라 %d건 지급 중단".formatted(reviewClaims.size()), decision.correlationId());
				}
				continue;
			}
			review.approve();
			for (RebateClaim claim : reviewClaims) {
				claim.requestPayment();
				payments.add(new PaymentsRequested.Payment(CLAIM_REFERENCE, claim.getRebateId(), claim.getAgencyId(),
						claim.getAmount().krw(), decision.correlationId()));
			}
			audit(Role.SYSTEM, "PAYMENT_REQUESTED", RebateReview.SUBJECT_TYPE, decision.subjectId(),
					"승인에 따라 %d건 지급 요청".formatted(reviewClaims.size()), decision.correlationId());
		}
		if (!payments.isEmpty()) {
			this.events.publishEvent(new PaymentsRequested(payments));
		}
		if (!clarifications.isEmpty()) {
			this.events.publishEvent(new ClarificationsRequested(clarifications));
		}
	}

	/**
	 * The agencies of a rejected group answered, or did not. An explanation puts the review before the
	 * executive again with the answer attached; silence hands it to audit.
	 */
	@ApplicationModuleListener
	void on(ClarificationClosed closed) {
		if (!ClarificationKind.RELATION_EXPLANATION.name().equals(closed.kind())
				|| !RebateReview.SUBJECT_TYPE.equals(closed.subjectType())) {
			return;
		}
		RebateReview review = this.reviews.findById(Long.valueOf(closed.subjectId())).orElseThrow();
		if (review.getStatus() != ReviewStatus.CLARIFICATION_REQUESTED) {
			audit(Role.SYSTEM, "CLARIFICATION_RESULT_IGNORED", RebateReview.SUBJECT_TYPE, closed.subjectId(),
					"검토가 이미 %s 상태라 소명 결과를 반영하지 않음".formatted(review.getStatus()), closed.correlationId());
			return;
		}
		if (ClarificationOutcome.NO_REPLY.name().equals(closed.outcome())) {
			referReviewToAudit(review, this.claims.findByReviewIdOrderByAgencyIdAsc(review.getId()),
					"관계 소명 요청 #%d 에 기한 내 회신이 없음".formatted(closed.clarificationId()), closed.correlationId());
			return;
		}
		review.resubmit("[소명 회신 · 모의 회신, 요청 #%d] %s".formatted(closed.clarificationId(), closed.replyText()));
		String title = "[재상신·소명 첨부] " + review.getTitle();
		this.events.publishEvent(new ApprovalRequestsSubmitted(List.of(new ApprovalRequestsSubmitted.Request(
				RebateReview.SUBJECT_TYPE, String.valueOf(review.getId()), review.getRequiredApprover().name(), title,
				review.getRationale(), review.getTotal().krw(), closed.correlationId()))));
		audit(Role.SYSTEM, "REVIEW_RESUBMITTED", RebateReview.SUBJECT_TYPE, closed.subjectId(),
				"소명 회신을 첨부해 %s 에게 %d차 상신".formatted(review.getRequiredApprover().label(), review.getRound()),
				closed.correlationId());
	}

	private ClarificationsRequested.Request relationClarification(RebateReview review, String comment) {
		String evidence = review.getRationale()
			.lines()
			.filter((line) -> line.startsWith("- D-") && line.contains("↔"))
			.collect(java.util.stream.Collectors.joining("\n"));
		String question = ("%s 판매장려금 지급 검토 중 아래 대리점의 등록 정보가 겹쳐 관련자로 합산되었습니다.%n%s%n"
				+ "공유하는 대표자·주소·계좌가 있는 정당한 사유(예: 동일 대표의 별도 법인, 공동 사무실 임차, 계좌 대리 관리)와 "
				+ "근거 서류(임대차계약서, 법인등기부, 계좌 위임 확인서 등)를 회신해 주십시오.%n"
				+ "이 요청은 사실 확인을 위한 것이며 부정을 확정한 것이 아닙니다.%s")
			.formatted(review.getPeriod(), evidence,
					comment == null || comment.isBlank() ? "" : "%n승인권자 반려 사유: %s".formatted(comment));
		return new ClarificationsRequested.Request(ClarificationKind.RELATION_EXPLANATION.name(),
				RebateReview.SUBJECT_TYPE, String.valueOf(review.getId()), String.join(",", review.getMemberAgencyIds()),
				"%s 관련자 그룹 %s 관계 소명 요청".formatted(review.getPeriod(), review.getUnitKey()), question,
				review.getCorrelationId());
	}

	private void referReviewToAudit(RebateReview review, List<RebateClaim> reviewClaims, String reason,
			String correlationId) {
		review.referToAudit();
		reviewClaims.stream()
			.filter((claim) -> claim.getSettlementStatus() == ClaimStatus.UNDER_APPROVAL)
			.forEach(RebateClaim::holdForAudit);
		this.auditLog.refer(new AuditReferral.Referral(RebateReview.SUBJECT_TYPE, String.valueOf(review.getId()),
				review.getTitle(), reason + " — 장려금 %d건 지급 보류".formatted(reviewClaims.size()), Role.SYSTEM.name(),
				correlationId));
	}

	private void decideFinding(ApprovalsDecided.Decision decision) {
		RetrospectiveFinding finding = this.findings.findById(Long.valueOf(decision.subjectId())).orElseThrow();
		if (finding.getStatus() != FindingStatus.AWAITING_CONFIRMATION) {
			return;
		}
		if (decision.approved()) {
			finding.confirm();
			audit(Role.SYSTEM, "FINDING_CONFIRMED", RetrospectiveFinding.SUBJECT_TYPE, decision.subjectId(),
					"%s이 전결 처리분을 사후 확인함%s".formatted(Role.valueOf(decision.decidedByRole()).label(),
							decision.comment().isBlank() ? "" : ": " + decision.comment()),
					decision.correlationId());
			return;
		}
		finding.referToAudit();
		this.auditLog.refer(new AuditReferral.Referral(RetrospectiveFinding.SUBJECT_TYPE, decision.subjectId(),
				finding.getTitle(), "%s이 사후 확인을 거절함%s".formatted(Role.valueOf(decision.decidedByRole()).label(),
						decision.comment().isBlank() ? "" : ": " + decision.comment()),
				Role.SYSTEM.name(), decision.correlationId()));
	}

	/**
	 * Marks paid claims and closes reviews whose claims are all paid. Reviews are locked in id order
	 * so that two replies touching the same reviews cannot deadlock and the last one sees every claim paid.
	 */
	@ApplicationModuleListener
	void on(PaymentsCompleted completed) {
		Map<String, String> correlationByClaim = new HashMap<>();
		for (PaymentsCompleted.Paid paid : completed.payments()) {
			if (CLAIM_REFERENCE.equals(paid.referenceType())) {
				correlationByClaim.put(paid.referenceId(), paid.correlationId());
			}
		}
		if (correlationByClaim.isEmpty()) {
			return;
		}
		List<RebateClaim> paidClaims = this.claims.findAllById(correlationByClaim.keySet());
		List<Long> reviewIds = paidClaims.stream().map(RebateClaim::getReviewId).distinct().sorted().toList();
		List<RebateReview> lockedReviews = reviewIds.stream()
			.map((id) -> this.reviews.findWithLockById(id).orElseThrow())
			.toList();
		paidClaims.stream()
			.filter((claim) -> claim.getSettlementStatus() == ClaimStatus.PAYMENT_REQUESTED)
			.forEach(RebateClaim::markPaid);
		for (RebateReview review : lockedReviews) {
			boolean allPaid = this.claims.findByReviewIdOrderByAgencyIdAsc(review.getId())
				.stream()
				.allMatch((each) -> each.getSettlementStatus() == ClaimStatus.PAID);
			if (allPaid && review.getStatus() == ReviewStatus.APPROVED) {
				review.markPaid();
				audit(Role.SYSTEM, "REVIEW_PAID", RebateReview.SUBJECT_TYPE, String.valueOf(review.getId()),
						"검토에 속한 장려금이 모두 지급 완료됨", review.getCorrelationId());
			}
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
