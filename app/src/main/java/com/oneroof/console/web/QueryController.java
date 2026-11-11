package com.oneroof.console.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oneroof.console.api.AuditEntryView;
import com.oneroof.console.api.ClaimView;
import com.oneroof.console.api.ConsoleQueryApi;
import com.oneroof.console.api.EvidenceView;
import com.oneroof.console.api.GroupView;
import com.oneroof.console.api.HistoryMonthView;
import com.oneroof.console.api.LinkView;
import com.oneroof.console.api.MatchView;
import com.oneroof.console.api.PaymentView;
import com.oneroof.console.api.PendingApprovalView;
import com.oneroof.console.api.PeriodView;
import com.oneroof.console.api.RelatedPartiesView;
import com.oneroof.console.api.ReviewDetailView;
import com.oneroof.console.api.ReviewView;
import com.oneroof.console.api.SessionView;
import com.oneroof.console.api.SessionView.CodeLabel;
import com.oneroof.console.api.SessionView.LimitPolicyView;
import com.oneroof.rebate.ExposureUnit;
import com.oneroof.rebate.RebateClaim;
import com.oneroof.rebate.RebateExposureService;
import com.oneroof.relatedparty.AgencyLink;
import com.oneroof.relatedparty.IdentifierMatch;
import com.oneroof.relatedparty.IdentityEvidence;
import com.oneroof.relatedparty.RelatedGroup;
import com.oneroof.relatedparty.RelatedPartyAnalysis;
import com.oneroof.relatedparty.RelatedPartyService;
import com.oneroof.shared.BusinessClock;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists come from the console views; evidence that depends on the domain rules (related groups,
 * monthly group totals) is read through the owning modules' services.
 */
@RestController
class QueryController implements ConsoleQueryApi {

	private static final String REVIEW_COLUMNS = "id, period, unit_key, related_group, member_agency_ids, total_krw, "
			+ "limit_krw, exceeds_limit, required_approver, status, title, correlation_id, approval_request_id, "
			+ "approval_status, decided_by_role, decided_by_name, decision_comment, claim_count, paid_count";

	private final ReadModel read;
	private final RelatedPartyService relatedParties;
	private final RebateExposureService exposure;
	private final BusinessClock clock;

	QueryController(ReadModel read, RelatedPartyService relatedParties, RebateExposureService exposure,
			BusinessClock clock) {
		this.read = read;
		this.relatedParties = relatedParties;
		this.exposure = exposure;
		this.clock = clock;
	}

	@Override
	public SessionView session() {
		List<CodeLabel> roles = Arrays.stream(Role.values())
			.filter((role) -> role != Role.SYSTEM)
			.map((role) -> new CodeLabel(role.name(), role.label()))
			.toList();
		List<CodeLabel> provenances = Arrays.stream(Provenance.values())
			.map((provenance) -> new CodeLabel(provenance.name(), provenance.label()))
			.toList();
		LimitPolicyView policy = this.read.one(LimitPolicyView.class,
				"SELECT monthly_limit_krw, effective_from, provenance, source_ref FROM rebate_limit_policy "
						+ "WHERE effective_from <= ? ORDER BY effective_from DESC LIMIT 1",
				this.clock.today());
		return new SessionView(this.clock.today(), roles, provenances, policy);
	}

	@Override
	public List<PeriodView> periods() {
		return this.read.list(PeriodView.class, "SELECT * FROM period_overview ORDER BY period DESC");
	}

	@Override
	public List<ReviewView> reviews(String period) {
		if (period != null) {
			return this.read.list(ReviewView.class, "SELECT " + REVIEW_COLUMNS
					+ " FROM review_overview WHERE period = ? ORDER BY exceeds_limit DESC, total_krw DESC", period);
		}
		return this.read.list(ReviewView.class,
				"SELECT " + REVIEW_COLUMNS + " FROM review_overview ORDER BY period DESC, exceeds_limit DESC, total_krw DESC");
	}

	@Override
	public ReviewDetailView review(long id) {
		ReviewRow row = this.read.one(ReviewRow.class, "SELECT * FROM review_overview WHERE id = ?", id);
		List<ClaimView> claims = this.read.list(ClaimView.class,
				"SELECT * FROM claim_overview WHERE review_id = ? ORDER BY agency_id", id);
		List<AuditEntryView> audit = this.read.list(AuditEntryView.class,
				"SELECT a.* FROM audit_log a WHERE a.correlation_id = ? AND ("
						+ "(a.subject_type = 'RebateReview' AND a.subject_id = ?) "
						+ "OR (a.subject_type = 'ApprovalRequest' AND a.subject_id = ?) "
						+ "OR (a.subject_type = 'PaymentInstruction' AND a.subject_id IN (SELECT payment_instruction_id::text "
						+ "FROM claim_overview WHERE review_id = ? AND payment_instruction_id IS NOT NULL))) ORDER BY a.id",
				row.correlationId(), String.valueOf(id), String.valueOf(row.approvalRequestId()), id);

		GroupView evidence = null;
		List<HistoryMonthView> history = null;
		if (row.relatedGroup()) {
			LocalDate today = this.clock.today();
			RelatedPartyAnalysis masterOnly = this.relatedParties.masterOnlyAnalysisAsOf(today);
			RelatedGroup group = this.relatedParties.analysisAsOf(today)
				.groupOf(row.memberAgencyIds().split(",")[0])
				.orElse(null);
			if (group != null) {
				evidence = groupView(group, masterOnly, null);
				history = history(group);
			}
		}
		return new ReviewDetailView(row.id(), row.period(), row.unitKey(), row.relatedGroup(), row.memberAgencyIds(),
				row.totalKrw(), row.limitKrw(), row.exceedsLimit(), row.requiredApprover(), row.status(), row.title(),
				row.rationale(), row.correlationId(), row.approvalRequestId(), row.approvalStatus(), row.decidedByRole(),
				row.decidedByName(), row.decidedOn(), row.decisionComment(), row.claimCount(), row.paidCount(), claims,
				audit, evidence, history);
	}

	@Override
	public List<PendingApprovalView> pendingApprovals(String role) {
		return this.read.list(PendingApprovalView.class,
				"SELECT a.id, a.subject_type, a.subject_id, a.required_role, a.title, a.amount_krw, a.status, "
						+ "a.requested_on, a.correlation_id, r.exceeds_limit, r.related_group, r.period "
						+ "FROM approval_request a LEFT JOIN review_overview r "
						+ "ON a.subject_type = 'RebateReview' AND a.subject_id = r.id::text "
						+ "WHERE a.required_role = ? AND a.status = 'PENDING' ORDER BY a.amount_krw DESC",
				role);
	}

	@Override
	public List<PaymentView> payments(String status) {
		if (status != null) {
			return this.read.list(PaymentView.class, "SELECT * FROM payment_overview WHERE status = ? ORDER BY id", status);
		}
		return this.read.list(PaymentView.class, "SELECT * FROM payment_overview ORDER BY id");
	}

	@Override
	public List<AuditEntryView> audit(String correlationId, int limit) {
		int capped = Math.max(1, Math.min(limit, 1000));
		if (correlationId != null) {
			return this.read.list(AuditEntryView.class,
					"SELECT * FROM audit_log WHERE correlation_id = ? ORDER BY id DESC LIMIT ?", correlationId, capped);
		}
		return this.read.list(AuditEntryView.class, "SELECT * FROM audit_log ORDER BY id DESC LIMIT ?", capped);
	}

	@Override
	public RelatedPartiesView relatedParties() {
		LocalDate today = this.clock.today();
		RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(today);
		RelatedPartyAnalysis masterOnly = this.relatedParties.masterOnlyAnalysisAsOf(today);
		List<GroupView> groups = analysis.groups()
			.stream()
			.map((group) -> groupView(group, masterOnly, history(group)))
			.toList();
		List<LinkView> nearMisses = analysis.nearMisses().stream().map((link) -> linkView(link, masterOnly)).toList();
		return new RelatedPartiesView(today, groups, nearMisses, this.read.list(RelatedPartiesView.AgencyView.class,
				"SELECT agency_id, name, rebate_eligible, registered_date FROM agency ORDER BY agency_id"));
	}

	/** The group's total for every month on record, to show how long the limit has been exceeded. */
	private List<HistoryMonthView> history(RelatedGroup group) {
		List<HistoryMonthView> history = new ArrayList<>();
		for (PeriodView period : this.read.list(PeriodView.class, "SELECT * FROM period_overview ORDER BY period")) {
			this.exposure.assess(YearMonth.parse(period.period()), this.clock.today())
				.stream()
				.filter((unit) -> unit.unitKey().equals(group.groupKey()))
				.findFirst()
				.ifPresent((unit) -> history.add(historyMonth(unit)));
		}
		return history;
	}

	private static HistoryMonthView historyMonth(ExposureUnit unit) {
		return new HistoryMonthView(unit.period().toString(), unit.total().krw(), unit.limit().krw(), unit.exceedsLimit(),
				unit.requiredApprover().name(),
				unit.claims().stream().map(RebateClaim::getOriginalApprover).filter((a) -> a != null).distinct().toList(),
				unit.claims().stream().map((claim) -> claim.getSettlementStatus().name()).distinct().toList());
	}

	private GroupView groupView(RelatedGroup group, RelatedPartyAnalysis masterOnly, List<HistoryMonthView> history) {
		List<GroupView.Member> members = this.read.list(GroupView.Member.class,
				"SELECT agency_id, name, business_reg_no, registered_date, source_ref FROM agency "
						+ "WHERE agency_id = ANY (?) ORDER BY agency_id",
				(Object) group.members().toArray(String[]::new));
		return new GroupView(group.groupKey(), members,
				group.links().stream().map((link) -> linkView(link, masterOnly)).toList(), history);
	}

	private static LinkView linkView(AgencyLink link, RelatedPartyAnalysis masterOnly) {
		boolean relatedInMaster = masterOnly.groups()
			.stream()
			.flatMap((group) -> group.links().stream())
			.anyMatch((other) -> other.agencyA().equals(link.agencyA()) && other.agencyB().equals(link.agencyB())
					&& other.isRelated());
		return new LinkView(link.agencyA(), link.agencyB(), link.isRelated(), relatedInMaster,
				link.matches().stream().map(QueryController::matchView).toList());
	}

	private static MatchView matchView(IdentifierMatch match) {
		return new MatchView(match.kind().name(), match.kind().label(), evidenceView(match.left()),
				evidenceView(match.right()));
	}

	private static EvidenceView evidenceView(IdentityEvidence evidence) {
		return new EvidenceView(evidence.agencyId(), evidence.rawValue(), evidence.basis().name(),
				evidence.basis().label(), evidence.provenance().name(), evidence.sourceRef());
	}

	/** Every column of {@code review_overview}, including the rationale the list leaves out. */
	record ReviewRow(long id, String period, String unitKey, boolean relatedGroup, String memberAgencyIds, long totalKrw,
			long limitKrw, boolean exceedsLimit, String requiredApprover, String status, String title, String rationale,
			String correlationId, Long approvalRequestId, String approvalStatus, String decidedByRole,
			String decidedByName, LocalDate decidedOn, String decisionComment, long claimCount, long paidCount) {
	}
}
