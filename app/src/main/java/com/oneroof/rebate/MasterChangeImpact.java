package com.oneroof.rebate;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oneroof.approval.ApprovalRequestsSubmitted;
import com.oneroof.approval.ApprovalRequestsWithdrawn;
import com.oneroof.audit.AuditLog;
import com.oneroof.audit.AuditRecord;
import com.oneroof.masterdata.AgencyIdentityCorrected;
import com.oneroof.masterdata.IdentityKind;
import com.oneroof.relatedparty.RelatedPartyAnalysis;
import com.oneroof.relatedparty.RelatedPartyService;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Follows a master correction through the rebates. Related groups are recomputed from the
 * corrected identity history, and then:
 * <ul>
 * <li>a review nobody has decided yet whose limit unit changed is replaced: its request is withdrawn
 * and the new unit goes to the role with authority over the new total;</li>
 * <li>a month already settled under the sales support team's authority whose new group total is
 * over the limit becomes a retrospective finding for the executive. Payments are not reversed.</li>
 * </ul>
 * Decided reviews are not reopened: a decision stands on what was known when it was made.
 */
@Service
class MasterChangeImpact {

	static final String SALES_SUPPORT_IN_SOURCE = "영업지원팀";

	private final SettlementBatchRepository batches;
	private final RebateReviewRepository reviews;
	private final RebateClaimRepository claims;
	private final RetrospectiveFindingRepository findings;
	private final RebateExposureService exposure;
	private final RelatedPartyService relatedParties;
	private final ReviewOpener opener;
	private final ApplicationEventPublisher events;
	private final AuditLog auditLog;
	private final BusinessClock clock;

	MasterChangeImpact(SettlementBatchRepository batches, RebateReviewRepository reviews, RebateClaimRepository claims,
			RetrospectiveFindingRepository findings, RebateExposureService exposure, RelatedPartyService relatedParties,
			ReviewOpener opener, ApplicationEventPublisher events, AuditLog auditLog, BusinessClock clock) {
		this.batches = batches;
		this.reviews = reviews;
		this.claims = claims;
		this.findings = findings;
		this.exposure = exposure;
		this.relatedParties = relatedParties;
		this.opener = opener;
		this.events = events;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	@ApplicationModuleListener
	void on(AgencyIdentityCorrected corrected) {
		String change = "마스터 정정 #%d: %s %s '%s' → '%s' (%s 부터, %s)".formatted(corrected.changeId(),
				corrected.agencyId(), IdentityKind.valueOf(corrected.kind()).label(), corrected.previousValue(),
				corrected.newValue(), corrected.effectiveFrom(), Provenance.valueOf(corrected.provenance()).label());
		int replaced = replaceStaleReviews(corrected, change);
		int found = findRetrospectively(corrected, change);
		audit("MASTER_CHANGE_ASSESSED", "Agency", corrected.agencyId(),
				"%s → 관련자 그룹 재계산: 결정 전 검토 %d건 재분류, 전결 처리 월 소급 발견 %d건".formatted(change, replaced, found),
				corrected.correlationId());
	}

	/** Replaces undecided reviews whose unit no longer matches the recomputed groups. */
	private int replaceStaleReviews(AgencyIdentityCorrected corrected, String change) {
		RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(this.clock.today());
		int replaced = 0;
		List<ApprovalRequestsWithdrawn.Withdrawal> withdrawals = new ArrayList<>();
		List<ApprovalRequestsSubmitted.Request> submissions = new ArrayList<>();
		List<YearMonth> openPeriods = this.reviews.findByStatusOrderByIdAsc(ReviewStatus.AWAITING_APPROVAL)
			.stream()
			.map(RebateReview::getPeriod)
			.distinct()
			.sorted()
			.toList();
		for (YearMonth period : openPeriods) {
			RebateLimitPolicy policy = this.exposure.policyFor(period);
			List<ExposureUnit> units = RebateExposureCalculator.calculate(period,
					this.claims.findByPeriodYmOrderByAgencyIdAsc(period.toString()), analysis, policy);
			Map<String, ExposureUnit> unitOfAgency = units.stream()
				.flatMap((unit) -> unit.agencyIds().stream().map((agency) -> Map.entry(agency, unit)))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

			List<RebateReview> stale = this.reviews.findByPeriodYmOrderByIdAsc(period.toString())
				.stream()
				.filter((review) -> review.getStatus() == ReviewStatus.AWAITING_APPROVAL)
				.filter((review) -> !matches(review, unitOfAgency.get(review.getMemberAgencyIds().get(0))))
				.toList();
			if (stale.isEmpty()) {
				continue;
			}
			Set<String> released = new HashSet<>();
			for (RebateReview review : stale) {
				review.supersede(corrected.changeId());
				for (RebateClaim claim : this.claims.findByReviewIdOrderByAgencyIdAsc(review.getId())) {
					if (claim.getSettlementStatus() == ClaimStatus.UNDER_APPROVAL) {
						claim.returnToPending();
						released.add(claim.getRebateId());
					}
				}
				withdrawals.add(new ApprovalRequestsWithdrawn.Withdrawal(RebateReview.SUBJECT_TYPE,
						String.valueOf(review.getId()), change + " 로 한도 단위가 바뀜", review.getCorrelationId()));
				audit("REVIEW_SUPERSEDED", RebateReview.SUBJECT_TYPE, String.valueOf(review.getId()),
						"%s — %s 로 한도 단위가 바뀌어 결정 전 검토를 대체".formatted(review.getTitle(), change),
						review.getCorrelationId());
			}
			List<ExposureUnit> reopened = units.stream()
				.filter((unit) -> unit.claims().stream().anyMatch((claim) -> released.contains(claim.getRebateId())))
				.toList();
			SettlementBatch batch = this.batches.findByPeriodYm(period.toString()).orElseThrow();
			submissions.addAll(this.opener.open(batch.getId(), reopened, analysis, policy,
					"[재분류] %s 로 한도 단위가 바뀌어 다시 상신함".formatted(change), stale.get(0).getCorrelationId()));
			replaced += stale.size();
		}
		if (!withdrawals.isEmpty()) {
			this.events.publishEvent(new ApprovalRequestsWithdrawn(withdrawals));
		}
		if (!submissions.isEmpty()) {
			this.events.publishEvent(new ApprovalRequestsSubmitted(submissions));
		}
		return replaced;
	}

	/**
	 * Looks at every month on record with the groups as registered at that month's end. A month
	 * becomes a finding when the corrected value is what ties the agency into its group there, the
	 * group was over the limit, and its claims were settled under the sales support team's authority,
	 * in the source data or in this system. A group that existed without the corrected value is not
	 * a finding of this correction.
	 */
	private int findRetrospectively(AgencyIdentityCorrected corrected, String change) {
		List<ApprovalRequestsSubmitted.Request> requests = new ArrayList<>();
		List<String> periods = this.claims.findAll()
			.stream()
			.map((claim) -> claim.getPeriod().toString())
			.distinct()
			.sorted()
			.toList();
		for (String periodText : periods) {
			YearMonth period = YearMonth.parse(periodText);
			if (!linkedByCorrection(corrected, period)) {
				continue;
			}
			this.exposure.assess(period, period.atEndOfMonth())
				.stream()
				.filter((unit) -> unit.relatedGroup() && unit.agencyIds().contains(corrected.agencyId()))
				.filter((unit) -> unit.requiredApprover() == Role.SALES_EXECUTIVE)
				.filter((unit) -> !this.findings.existsByPeriodYmAndUnitKey(periodText, unit.unitKey()))
				.forEach((unit) -> {
					List<RebateClaim> settled = settledUnderSalesSupport(unit.claims());
					if (settled.isEmpty()) {
						return;
					}
					String title = "%s 관련자 그룹 %s 합산 %,d원 — 전결 처리분 사후 확인".formatted(period, unit.unitKey(),
							unit.total().krw());
					String rationale = rationale(unit, settled, change);
					RetrospectiveFinding finding = this.findings.save(new RetrospectiveFinding(unit, settled,
							SALES_SUPPORT_IN_SOURCE, corrected.changeId(), title, rationale, corrected.correlationId()));
					audit("FINDING_OPENED", RetrospectiveFinding.SUBJECT_TYPE, String.valueOf(finding.getId()),
							title + " → 영업임원 사후 확인 요청", corrected.correlationId());
					requests.add(new ApprovalRequestsSubmitted.Request(RetrospectiveFinding.SUBJECT_TYPE,
							String.valueOf(finding.getId()), Role.SALES_EXECUTIVE.name(), title, rationale,
							unit.total().krw(), corrected.correlationId()));
				});
		}
		if (!requests.isEmpty()) {
			this.events.publishEvent(new ApprovalRequestsSubmitted(requests));
		}
		return requests.size();
	}

	/** Whether, at the month's end, a related link of the corrected agency rests on the corrected value. */
	private boolean linkedByCorrection(AgencyIdentityCorrected corrected, YearMonth period) {
		String correctedRow = "master_change#" + corrected.changeId();
		return this.relatedParties.analysisAsOf(period.atEndOfMonth())
			.groupOf(corrected.agencyId())
			.stream()
			.flatMap((group) -> group.links().stream())
			.filter((link) -> link.isRelated() && link.connects(corrected.agencyId()))
			.flatMap((link) -> link.matches().stream())
			.anyMatch((match) -> correctedRow.equals(match.left().sourceRef())
					|| correctedRow.equals(match.right().sourceRef()));
	}

	/** Claims approved under the sales support team's authority: in the source file, or by a review it decided here. */
	private List<RebateClaim> settledUnderSalesSupport(List<RebateClaim> unitClaims) {
		Set<Long> reviewIds = unitClaims.stream()
			.map(RebateClaim::getReviewId)
			.filter((id) -> id != null)
			.collect(Collectors.toSet());
		Map<Long, RebateReview> byId = this.reviews.findAllById(reviewIds)
			.stream()
			.collect(Collectors.toMap(RebateReview::getId, Function.identity()));
		return unitClaims.stream().filter((claim) -> switch (claim.getSettlementStatus()) {
			case SETTLED_IN_SOURCE -> SALES_SUPPORT_IN_SOURCE.equals(claim.getOriginalApprover());
			case PAYMENT_REQUESTED, PAID -> {
				RebateReview review = byId.get(claim.getReviewId());
				yield review != null && review.getRequiredApprover() == Role.SALES_SUPPORT;
			}
			default -> false;
		}).toList();
	}

	private static String rationale(ExposureUnit unit, List<RebateClaim> settled, String change) {
		StringBuilder text = new StringBuilder();
		text.append(change).append(" 이후 다시 계산한 결과입니다.\n");
		text.append("%s 그룹 %s 합산 %,d원 > 월 한도 %,d원 → 승인 권한 %s%n".formatted(unit.period(),
				String.join("·", unit.agencyIds()), unit.total().krw(), unit.limit().krw(),
				unit.requiredApprover().label()));
		text.append("그런데 아래 건은 영업지원팀 전결로 처리되었습니다:\n");
		for (RebateClaim claim : settled) {
			text.append("- %s %s %,d원 (%s, %s)%n".formatted(claim.getAgencyId(), claim.getRebateId(),
					claim.getAmount().krw(),
					claim.getSettlementStatus() == ClaimStatus.SETTLED_IN_SOURCE
							? "원본 승인자 " + claim.getOriginalApprover() + ", 지급일 " + claim.getOriginalPaymentDate()
							: "이 시스템에서 전결 승인",
					claim.getSourceRef()));
		}
		text.append("지급은 되돌리지 않습니다. 사후 확인하거나, 확인하지 않으면 감사로 이관합니다. "
				+ "정정 값의 적용일은 신고 내용(부서 주장)을 따랐습니다.");
		return text.toString();
	}

	private static boolean matches(RebateReview review, ExposureUnit unit) {
		return unit != null && unit.unitKey().equals(review.getUnitKey())
				&& unit.agencyIds().equals(review.getMemberAgencyIds()) && unit.total().krw() == review.getTotal().krw();
	}

	private void audit(String action, String subjectType, String subjectId, String detail, String correlationId) {
		this.auditLog.record(new AuditRecord(Role.SYSTEM.name(), action, subjectType, subjectId, detail,
				Provenance.CALCULATED, correlationId));
	}
}
