package com.oneroof.console.web;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import com.oneroof.approval.ApprovalRequest;
import com.oneroof.approval.ApprovalService;
import com.oneroof.clarification.ClarificationService;
import com.oneroof.console.api.Commands.BankRepliesResult;
import com.oneroof.console.api.Commands.BankReplyRequest;
import com.oneroof.console.api.Commands.BulkDecisionRequest;
import com.oneroof.console.api.Commands.BulkDecisionResult;
import com.oneroof.console.api.Commands.ClarificationReplyRequest;
import com.oneroof.console.api.Commands.DepositRequest;
import com.oneroof.console.api.Commands.ResetResult;
import com.oneroof.console.api.Commands.MasterChangeRequest;
import com.oneroof.console.api.Commands.DecisionRequest;
import com.oneroof.console.api.Commands.DecisionResult;
import com.oneroof.console.api.Commands.ReceiptView;
import com.oneroof.console.api.Commands.SettlementRequest;
import com.oneroof.console.api.ConsoleCommandApi;
import com.oneroof.intake.IntakeService;
import com.oneroof.intake.PaymentResultsReceived;
import com.oneroof.intake.Receipt;
import com.oneroof.masterdata.MasterCorrectionService;
import com.oneroof.payment.PaymentService;
import com.oneroof.platform.DemoReset;
import com.oneroof.receivables.ReceivablesLedger;
import com.oneroof.shared.Role;
import org.springframework.web.bind.annotation.RestController;

/** Passes each command to the module that owns it. No business rule is decided here. */
@RestController
class CommandController implements ConsoleCommandApi {

	private final IntakeService intake;
	private final ApprovalService approvals;
	private final PaymentService payments;
	private final MasterCorrectionService masterCorrections;
	private final ClarificationService clarifications;
	private final ReceivablesLedger receivables;
	private final DemoReset demoReset;

	CommandController(IntakeService intake, ApprovalService approvals, PaymentService payments,
			MasterCorrectionService masterCorrections, ClarificationService clarifications,
			ReceivablesLedger receivables, DemoReset demoReset) {
		this.demoReset = demoReset;
		this.receivables = receivables;
		this.intake = intake;
		this.approvals = approvals;
		this.payments = payments;
		this.masterCorrections = masterCorrections;
		this.clarifications = clarifications;
	}

	/**
	 * Checked here first so a mistyped correction is refused at once. The event id is derived from
	 * the content, so submitting the same correction twice is harmless.
	 */
	@Override
	public ReceiptView reportMasterChange(String role, MasterChangeRequest request) {
		Role actor = actor(role);
		this.masterCorrections
			.problemWith(request.agencyId(), request.kind(), request.newValue(), request.effectiveFrom())
			.ifPresent((problem) -> {
				throw new IllegalArgumentException(problem);
			});
		String eventId = "EVT-MASTER-%s-%s-%08x".formatted(request.agencyId(), request.kind(),
				(request.newValue().trim() + "|" + request.effectiveFrom()).hashCode());
		return receipt(this.intake.receiveMasterChange(eventId, request.agencyId(), request.kind(),
				request.newValue().trim(), request.effectiveFrom(), request.reason(), actor.name()));
	}

	@Override
	public ResetResult resetDemo(String role) {
		return new ResetResult(this.demoReset.reset(actor(role).name()));
	}

	/** The event id is derived from the content, so entering the same deposit twice is harmless. */
	@Override
	public ReceiptView reportDeposit(String role, String id, DepositRequest request) {
		Role actor = actor(role);
		this.receivables.problemWithDeposit(id, request.amountKrw()).ifPresent((problem) -> {
			throw new IllegalArgumentException(problem);
		});
		String eventId = "EVT-DEPOSIT-%s-%d-%08x".formatted(id, request.amountKrw(),
				request.bankAccount().trim().hashCode());
		return receipt(this.intake.receiveAgencyDeposit(eventId, id, request.amountKrw(), request.bankAccount().trim(),
				actor.name()));
	}

	/** One reply per request: the event id is the request's, so a second reply is acknowledged and ignored. */
	@Override
	public ReceiptView replyToClarification(long id, ClarificationReplyRequest request) {
		this.clarifications.problemWith(id, request.outcome(), request.replyText(), request.replyAccount()).ifPresent((problem) -> {
			throw new IllegalArgumentException(problem);
		});
		return receipt(this.intake.receiveClarificationReply("EVT-CLARIFY-" + id, id, request.outcome(),
				request.replyText(), request.replyAccount()));
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

	/**
	 * Replies for every instruction waiting at the bank as one bulk reply. The reply is only stored
	 * here; it is applied in the background, so the call returns at once. The event id is derived
	 * from the instructions covered, so repeating the same reply is harmless.
	 */
	@Override
	public BankRepliesResult bankReplies() {
		List<PaymentResultsReceived.Result> results = this.payments.awaitingBankReply()
			.stream()
			.map((instruction) -> new PaymentResultsReceived.Result(instruction.getId(), true,
					bankReference(instruction.getId())))
			.toList();
		if (results.isEmpty()) {
			return new BankRepliesResult(0);
		}
		String covered = results.stream()
			.map((result) -> String.valueOf(result.instructionId()))
			.collect(Collectors.joining(","));
		this.intake.receivePaymentResults("EVT-BANK-BULK-" + Integer.toHexString(covered.hashCode()) + "-"
				+ results.size(), results);
		return new BankRepliesResult(results.size());
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
