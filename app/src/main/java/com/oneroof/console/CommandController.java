package com.oneroof.console;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.oneroof.approval.ApprovalRequest;
import com.oneroof.approval.ApprovalService;
import com.oneroof.intake.IntakeService;
import com.oneroof.intake.Receipt;
import com.oneroof.shared.Role;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything the console can ask the system to do. The acting role comes from the role switcher
 * in the console (a simulation of signing in as that role); whether the role has the authority is
 * decided by the owning module, not here.
 */
@RestController
@RequestMapping("/api")
class CommandController {

	static final String ROLE_HEADER = "X-Oneroof-Role";

	private final IntakeService intake;
	private final ApprovalService approvals;
	private final ReadModel read;

	CommandController(IntakeService intake, ApprovalService approvals, ReadModel read) {
		this.intake = intake;
		this.approvals = approvals;
		this.read = read;
	}

	/** Submits a month for settlement. The event id is derived from the month, so submitting twice is harmless. */
	@PostMapping("/settlements")
	Receipt submitSettlement(@RequestHeader(ROLE_HEADER) String role, @RequestBody SettlementRequest request) {
		YearMonth period = YearMonth.parse(request.period());
		return this.intake.receiveSettlementBatch("EVT-SETTLEMENT-" + period, period, actor(role).name());
	}

	@PostMapping("/approvals/{id}/decision")
	Map<String, Object> decide(@RequestHeader(ROLE_HEADER) String role, @PathVariable long id,
			@RequestBody DecisionRequest request) {
		Role actor = actor(role);
		ApprovalRequest decided = this.approvals.decide(id, actor, actorName(actor), request.approved(),
				request.comment());
		return Map.of("id", decided.getId(), "status", decided.getStatus().name());
	}

	@PostMapping("/approvals/decisions")
	Map<String, Object> decideAll(@RequestHeader(ROLE_HEADER) String role, @RequestBody BulkDecisionRequest request) {
		Role actor = actor(role);
		List<ApprovalRequest> decided = this.approvals.decideAll(request.ids(), actor, actorName(actor),
				request.approved(), request.comment());
		return Map.of("decided", decided.size());
	}

	/** Enters the bank's reply for one instruction. A simulation of the bank calling back. */
	@PostMapping("/payments/{id}/bank-reply")
	Receipt bankReply(@PathVariable long id, @RequestBody BankReplyRequest request) {
		return this.intake.receivePaymentResult("EVT-BANK-" + id, id, request.succeeded(), "MOCK-BANK-TX-%06d".formatted(id));
	}

	/** Enters a successful bank reply for every instruction that is waiting for one. */
	@PostMapping("/payments/bank-replies")
	Map<String, Object> bankReplies() {
		List<Map<String, Object>> waiting = this.read.rows("SELECT id FROM payment_overview WHERE status = 'SENT' ORDER BY id");
		for (Map<String, Object> row : waiting) {
			long id = ((Number) row.get("id")).longValue();
			this.intake.receivePaymentResult("EVT-BANK-" + id, id, true, "MOCK-BANK-TX-%06d".formatted(id));
		}
		return Map.of("replied", waiting.size());
	}

	private static Role actor(String role) {
		Role actor = Role.valueOf(role);
		if (actor == Role.SYSTEM) {
			throw new IllegalArgumentException("시스템 역할로는 직접 행동할 수 없음");
		}
		return actor;
	}

	private static String actorName(Role role) {
		return role.label() + " 담당(시연)";
	}

	record SettlementRequest(String period) {
	}

	record DecisionRequest(boolean approved, String comment) {
	}

	record BulkDecisionRequest(List<Long> ids, boolean approved, String comment) {
	}

	record BankReplyRequest(boolean succeeded) {
	}
}
