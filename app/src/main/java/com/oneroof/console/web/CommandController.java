package com.oneroof.console.web;

import java.time.YearMonth;
import java.util.List;

import com.oneroof.approval.ApprovalRequest;
import com.oneroof.approval.ApprovalService;
import com.oneroof.console.api.Commands.BankRepliesResult;
import com.oneroof.console.api.Commands.BankReplyRequest;
import com.oneroof.console.api.Commands.BulkDecisionRequest;
import com.oneroof.console.api.Commands.BulkDecisionResult;
import com.oneroof.console.api.Commands.DecisionRequest;
import com.oneroof.console.api.Commands.DecisionResult;
import com.oneroof.console.api.Commands.ReceiptView;
import com.oneroof.console.api.Commands.SettlementRequest;
import com.oneroof.console.api.ConsoleCommandApi;
import com.oneroof.intake.IntakeService;
import com.oneroof.intake.Receipt;
import com.oneroof.payment.PaymentInstruction;
import com.oneroof.payment.PaymentService;
import com.oneroof.shared.Role;
import org.springframework.web.bind.annotation.RestController;

/** Passes each command to the module that owns it. No business rule is decided here. */
@RestController
class CommandController implements ConsoleCommandApi {

	private final IntakeService intake;
	private final ApprovalService approvals;
	private final PaymentService payments;

	CommandController(IntakeService intake, ApprovalService approvals, PaymentService payments) {
		this.intake = intake;
		this.approvals = approvals;
		this.payments = payments;
	}

	/** The event id is derived from the month, so submitting the same month twice is harmless. */
	@Override
	public ReceiptView submitSettlement(String role, SettlementRequest request) {
		YearMonth period = YearMonth.parse(request.period());
		return receipt(this.intake.receiveSettlementBatch("EVT-SETTLEMENT-" + period, period, actor(role).name()));
	}

	@Override
	public DecisionResult decide(String role, long id, DecisionRequest request) {
		Role actor = actor(role);
		ApprovalRequest decided = this.approvals.decide(id, actor, actorName(actor), request.approved(),
				request.comment());
		return new DecisionResult(decided.getId(), decided.getStatus().name());
	}

	@Override
	public BulkDecisionResult decideAll(String role, BulkDecisionRequest request) {
		Role actor = actor(role);
		return new BulkDecisionResult(
				this.approvals.decideAll(request.ids(), actor, actorName(actor), request.approved(), request.comment())
					.size());
	}

	@Override
	public ReceiptView bankReply(long id, BankReplyRequest request) {
		return receipt(this.intake.receivePaymentResult("EVT-BANK-" + id, id, request.succeeded(), bankReference(id)));
	}

	@Override
	public BankRepliesResult bankReplies() {
		List<PaymentInstruction> waiting = this.payments.awaitingBankReply();
		for (PaymentInstruction instruction : waiting) {
			long id = instruction.getId();
			this.intake.receivePaymentResult("EVT-BANK-" + id, id, true, bankReference(id));
		}
		return new BankRepliesResult(waiting.size());
	}

	private static Role actor(String role) {
		Role actor;
		try {
			actor = Role.valueOf(role);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("알 수 없는 역할: " + role);
		}
		if (actor == Role.SYSTEM) {
			throw new IllegalArgumentException("시스템 역할로는 직접 행동할 수 없음");
		}
		return actor;
	}

	private static String actorName(Role role) {
		return role.label() + " 담당(시연)";
	}

	private static String bankReference(long instructionId) {
		return "MOCK-BANK-TX-%06d".formatted(instructionId);
	}

	private static ReceiptView receipt(Receipt receipt) {
		return new ReceiptView(receipt.eventId(), receipt.accepted());
	}
}
