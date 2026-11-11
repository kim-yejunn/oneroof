package com.oneroof.console;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Everything the console reads. */
@RestController
@RequestMapping("/api")
class QueryController {

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

	@GetMapping("/session")
	Map<String, Object> session() {
		Map<String, Object> session = new LinkedHashMap<>();
		session.put("businessDate", this.clock.today().toString());
		session.put("roles", Arrays.stream(Role.values())
			.filter((role) -> role != Role.SYSTEM)
			.map((role) -> Map.of("code", role.name(), "label", role.label()))
			.toList());
		session.put("provenances", Arrays.stream(Provenance.values())
			.map((provenance) -> Map.of("code", provenance.name(), "label", provenance.label()))
			.toList());
		session.put("limitPolicy", this.read.row(
				"SELECT monthly_limit_krw, effective_from, provenance, source_ref FROM rebate_limit_policy "
						+ "WHERE effective_from <= ? ORDER BY effective_from DESC LIMIT 1",
				this.clock.today()));
		return session;
	}

	@GetMapping("/periods")
	List<Map<String, Object>> periods() {
		return this.read.rows("SELECT * FROM period_overview ORDER BY period DESC");
	}

	@GetMapping("/reviews")
	List<Map<String, Object>> reviews(@RequestParam Optional<String> period) {
		String columns = "id, period, unit_key, related_group, member_agency_ids, total_krw, limit_krw, exceeds_limit, "
				+ "required_approver, status, title, correlation_id, approval_request_id, approval_status, "
				+ "decided_by_role, decided_by_name, decision_comment, claim_count, paid_count";
		if (period.isPresent()) {
			return this.read.rows("SELECT " + columns
					+ " FROM review_overview WHERE period = ? ORDER BY exceeds_limit DESC, total_krw DESC", period.get());
		}
		return this.read
			.rows("SELECT " + columns + " FROM review_overview ORDER BY period DESC, exceeds_limit DESC, total_krw DESC");
	}

	@GetMapping("/reviews/{id}")
	Map<String, Object> review(@PathVariable long id) {
		Map<String, Object> review = new LinkedHashMap<>(
				this.read.row("SELECT * FROM review_overview WHERE id = ?", id));
		review.put("claims", this.read.rows("SELECT * FROM claim_overview WHERE review_id = ? ORDER BY agency_id", id));
		review.put("audit", this.read.rows("SELECT a.* FROM audit_log a WHERE a.correlation_id = ? AND ("
				+ "(a.subject_type = 'RebateReview' AND a.subject_id = ?) "
				+ "OR (a.subject_type = 'ApprovalRequest' AND a.subject_id = ?) "
				+ "OR (a.subject_type = 'PaymentInstruction' AND a.subject_id IN "
				+ "(SELECT payment_instruction_id::text FROM claim_overview WHERE review_id = ? AND payment_instruction_id IS NOT NULL))"
				+ ") ORDER BY a.id", review.get("correlationId"), String.valueOf(id),
				String.valueOf(review.get("approvalRequestId")), id));
		if (Boolean.TRUE.equals(review.get("relatedGroup"))) {
			String firstMember = String.valueOf(review.get("memberAgencyIds")).split(",")[0];
			LocalDate today = this.clock.today();
			RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(today);
			RelatedPartyAnalysis masterOnly = this.relatedParties.masterOnlyAnalysisAsOf(today);
			analysis.groupOf(firstMember).ifPresent((group) -> {
				review.put("evidence", groupJson(group, masterOnly));
				review.put("history", history(group));
			});
		}
		return review;
	}

	@GetMapping("/approvals/pending")
	List<Map<String, Object>> pendingApprovals(@RequestParam String role) {
		return this.read.rows("SELECT a.id, a.subject_type, a.subject_id, a.required_role, a.title, a.amount_krw, "
				+ "a.status, a.requested_on, a.correlation_id, r.exceeds_limit, r.related_group, r.period "
				+ "FROM approval_request a LEFT JOIN review_overview r ON a.subject_type = 'RebateReview' AND a.subject_id = r.id::text "
				+ "WHERE a.required_role = ? AND a.status = 'PENDING' ORDER BY a.amount_krw DESC", role);
	}

	@GetMapping("/payments")
	List<Map<String, Object>> payments(@RequestParam Optional<String> status) {
		if (status.isPresent()) {
			return this.read.rows("SELECT * FROM payment_overview WHERE status = ? ORDER BY id", status.get());
		}
		return this.read.rows("SELECT * FROM payment_overview ORDER BY id");
	}

	@GetMapping("/audit")
	List<Map<String, Object>> audit(@RequestParam Optional<String> correlationId,
			@RequestParam(defaultValue = "300") int limit) {
		int capped = Math.max(1, Math.min(limit, 1000));
		if (correlationId.isPresent()) {
			return this.read.rows("SELECT * FROM audit_log WHERE correlation_id = ? ORDER BY id DESC LIMIT ?",
					correlationId.get(), capped);
		}
		return this.read.rows("SELECT * FROM audit_log ORDER BY id DESC LIMIT ?", capped);
	}

	@GetMapping("/related-parties")
	Map<String, Object> relatedParties() {
		LocalDate today = this.clock.today();
		RelatedPartyAnalysis analysis = this.relatedParties.analysisAsOf(today);
		RelatedPartyAnalysis masterOnly = this.relatedParties.masterOnlyAnalysisAsOf(today);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("asOf", today.toString());
		result.put("groups", analysis.groups().stream().map((group) -> {
			Map<String, Object> json = groupJson(group, masterOnly);
			json.put("history", history(group));
			return json;
		}).toList());
		result.put("nearMisses", analysis.nearMisses().stream().map((link) -> linkJson(link, masterOnly)).toList());
		result.put("agencies", this.read.rows("SELECT agency_id, name, rebate_eligible, registered_date FROM agency ORDER BY agency_id"));
		return result;
	}

	/** The group's total for every month on record, to show how long the limit has been exceeded. */
	private List<Map<String, Object>> history(RelatedGroup group) {
		List<Map<String, Object>> history = new ArrayList<>();
		for (Map<String, Object> row : this.read.rows("SELECT period FROM period_overview ORDER BY period")) {
			YearMonth period = YearMonth.parse(String.valueOf(row.get("period")));
			this.exposure.assess(period, this.clock.today())
				.stream()
				.filter((unit) -> unit.unitKey().equals(group.groupKey()))
				.findFirst()
				.ifPresent((unit) -> history.add(historyJson(unit)));
		}
		return history;
	}

	private static Map<String, Object> historyJson(ExposureUnit unit) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("period", unit.period().toString());
		json.put("totalKrw", unit.total().krw());
		json.put("limitKrw", unit.limit().krw());
		json.put("exceedsLimit", unit.exceedsLimit());
		json.put("requiredApprover", unit.requiredApprover().name());
		json.put("originalApprovers",
				unit.claims().stream().map(RebateClaim::getOriginalApprover).filter((a) -> a != null).distinct().toList());
		json.put("statuses",
				unit.claims().stream().map((claim) -> claim.getSettlementStatus().name()).distinct().toList());
		return json;
	}

	private Map<String, Object> groupJson(RelatedGroup group, RelatedPartyAnalysis masterOnly) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("groupKey", group.groupKey());
		json.put("members", this.read.rows(
				"SELECT agency_id, name, business_reg_no, registered_date, source_ref FROM agency WHERE agency_id = ANY (?) ORDER BY agency_id",
				(Object) group.members().toArray(String[]::new)));
		json.put("links", group.links().stream().map((link) -> linkJson(link, masterOnly)).toList());
		return json;
	}

	private static Map<String, Object> linkJson(AgencyLink link, RelatedPartyAnalysis masterOnly) {
		boolean relatedInMaster = masterOnly.groups()
			.stream()
			.flatMap((group) -> group.links().stream())
			.anyMatch((other) -> other.agencyA().equals(link.agencyA()) && other.agencyB().equals(link.agencyB())
					&& other.isRelated());
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("agencyA", link.agencyA());
		json.put("agencyB", link.agencyB());
		json.put("related", link.isRelated());
		json.put("relatedInMasterOnly", relatedInMaster);
		json.put("matches", link.matches().stream().map(QueryController::matchJson).toList());
		return json;
	}

	private static Map<String, Object> matchJson(IdentifierMatch match) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("kind", match.kind().name());
		json.put("kindLabel", match.kind().label());
		json.put("left", evidenceJson(match.left()));
		json.put("right", evidenceJson(match.right()));
		return json;
	}

	private static Map<String, Object> evidenceJson(IdentityEvidence evidence) {
		Map<String, Object> json = new LinkedHashMap<>();
		json.put("agencyId", evidence.agencyId());
		json.put("value", evidence.rawValue());
		json.put("basis", evidence.basis().name());
		json.put("basisLabel", evidence.basis().label());
		json.put("provenance", evidence.provenance().name());
		json.put("sourceRef", evidence.sourceRef());
		return json;
	}
}
