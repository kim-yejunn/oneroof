package com.oneroof.payment;

import java.time.LocalDate;

import com.oneroof.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** An order to pay an approved amount to an agency, and how far it has got. */
@Entity
@Table(name = "payment_instruction")
public class PaymentInstruction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reference_type", nullable = false)
	private String referenceType;

	@Column(name = "reference_id", nullable = false)
	private String referenceId;

	@Column(name = "agency_id", nullable = false)
	private String agencyId;

	@Column(name = "amount_krw", nullable = false)
	private long amountKrw;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(name = "gateway_reference")
	private String gatewayReference;

	@Column(name = "bank_reference")
	private String bankReference;

	@Column(name = "payee_account")
	private String payeeAccount;

	@Column(name = "hold_reason")
	private String holdReason;

	/** Part of the amount set off against the agency's overdue receivables; the rest goes to the bank. */
	@Column(name = "offset_krw", nullable = false)
	private long offsetKrw;

	/** The overdue invoices the offset rests on, as shown to finance. */
	@Column(name = "offset_basis")
	private String offsetBasis;

	/** 0 without an offset, 1 for finance's first decision, 2 after the agency disputed. */
	@Column(name = "offset_round", nullable = false)
	private int offsetRound;

	/** What finance decided and why, in words. */
	@Column(name = "offset_decision")
	private String offsetDecision;

	@Column(name = "requested_on", nullable = false)
	private LocalDate requestedOn;

	@Column(name = "correlation_id", nullable = false)
	private String correlationId;

	@Version
	private long version;

	protected PaymentInstruction() {
	}

	/**
	 * @param holdReason why the payee account has to be confirmed before sending, or {@code null}
	 * when it can be sent
	 */
	PaymentInstruction(PaymentsRequested.Payment requested, String payeeAccount, String holdReason,
			LocalDate requestedOn) {
		this.referenceType = requested.referenceType();
		this.referenceId = requested.referenceId();
		this.agencyId = requested.agencyId();
		this.amountKrw = requested.amountKrw();
		this.payeeAccount = payeeAccount;
		this.holdReason = holdReason;
		this.status = holdReason == null ? PaymentStatus.REQUESTED : PaymentStatus.HELD;
		this.requestedOn = requestedOn;
		this.correlationId = requested.correlationId();
	}

	/** An instruction for an agency with long-overdue receivables: finance decides on the offset before anything else. */
	PaymentInstruction(PaymentsRequested.Payment requested, long proposedOffsetKrw, String offsetBasis,
			LocalDate requestedOn) {
		this(requested, null, null, requestedOn);
		this.status = PaymentStatus.OFFSET_REVIEW;
		this.offsetKrw = proposedOffsetKrw;
		this.offsetBasis = offsetBasis;
		this.offsetRound = 1;
	}

	/** The proposal changed before finance decided, for example because the agency paid in. */
	void reviseOffset(long proposedOffsetKrw, String offsetBasis) {
		requireStatus(PaymentStatus.OFFSET_REVIEW);
		this.offsetKrw = proposedOffsetKrw;
		this.offsetBasis = offsetBasis;
	}

	/**
	 * Finance set the amount off. When it covers the whole amount nothing is left to send; otherwise
	 * the rest still needs a payee account, see {@link #routeToPayee}.
	 */
	void settleOffset(long offsetKrw, String decision) {
		requireStatus(PaymentStatus.OFFSET_REVIEW);
		if (offsetKrw <= 0 || offsetKrw > this.amountKrw) {
			throw new IllegalArgumentException("상계액 %,d원이 지급액 %,d원 범위를 벗어남".formatted(offsetKrw, this.amountKrw));
		}
		this.offsetKrw = offsetKrw;
		this.offsetDecision = decision;
		if (offsetKrw == this.amountKrw) {
			this.status = PaymentStatus.OFFSET_SETTLED;
		}
	}

	/** Finance chose to pay the whole amount and collect the receivables separately. */
	void waiveOffset(String decision) {
		requireStatus(PaymentStatus.OFFSET_REVIEW);
		this.offsetKrw = 0;
		this.offsetDecision = decision;
	}

	/** The agency disputed a full offset; finance looks at it again. */
	void dispute(String reason) {
		requireStatus(PaymentStatus.OFFSET_SETTLED);
		this.status = PaymentStatus.OFFSET_DISPUTED;
		this.offsetRound = 2;
		this.offsetDecision = this.offsetDecision + " / 대리점 이의: " + reason;
	}

	/** Finance kept the offset after the dispute. */
	void upholdOffset(String decision) {
		requireStatus(PaymentStatus.OFFSET_DISPUTED);
		this.status = PaymentStatus.OFFSET_SETTLED;
		this.offsetDecision = this.offsetDecision + " / " + decision;
	}

	/** Finance cancelled the offset after the dispute; the whole amount is paid. */
	void cancelOffset(String decision) {
		requireStatus(PaymentStatus.OFFSET_DISPUTED);
		this.offsetKrw = 0;
		this.status = PaymentStatus.OFFSET_REVIEW;
		this.offsetDecision = this.offsetDecision + " / " + decision;
	}

	/** After the offset question is settled, the amount still to pay goes to the bank or waits for the account. */
	void routeToPayee(String payeeAccount, String holdReason) {
		requireStatus(PaymentStatus.OFFSET_REVIEW);
		this.payeeAccount = payeeAccount;
		this.holdReason = holdReason;
		this.status = holdReason == null ? PaymentStatus.REQUESTED : PaymentStatus.HELD;
	}

	/** Lets a held instruction go to the bank, paying the account that was confirmed. */
	void release(String confirmedAccount) {
		requireStatus(PaymentStatus.HELD);
		this.payeeAccount = confirmedAccount;
		this.status = PaymentStatus.REQUESTED;
	}

	/** Keeps the hold and says why it continues. */
	void extendHold(String reason) {
		requireStatus(PaymentStatus.HELD);
		this.holdReason = this.holdReason + " / " + reason;
	}

	void markSent(String gatewayReference) {
		requireStatus(PaymentStatus.REQUESTED);
		this.status = PaymentStatus.SENT;
		this.gatewayReference = gatewayReference;
	}

	void recordBankReply(boolean succeeded, String bankReference) {
		requireStatus(PaymentStatus.SENT);
		this.status = succeeded ? PaymentStatus.PAID : PaymentStatus.FAILED;
		this.bankReference = bankReference;
	}

	private void requireStatus(PaymentStatus expected) {
		if (this.status != expected) {
			throw new IllegalStateException(
					"지급 지시 #%d 은 %s 상태라 처리할 수 없음 (필요 상태 %s)".formatted(this.id, this.status, expected));
		}
	}

	public Long getId() {
		return this.id;
	}

	public String getReferenceType() {
		return this.referenceType;
	}

	public String getReferenceId() {
		return this.referenceId;
	}

	public String getAgencyId() {
		return this.agencyId;
	}

	public Money getAmount() {
		return Money.ofKrw(this.amountKrw);
	}

	public Money getOffset() {
		return Money.ofKrw(this.offsetKrw);
	}

	/** What goes to the bank: the amount owed less the offset. */
	public Money getNetAmount() {
		return Money.ofKrw(this.amountKrw - this.offsetKrw);
	}

	public String getOffsetBasis() {
		return this.offsetBasis;
	}

	public int getOffsetRound() {
		return this.offsetRound;
	}

	public String getOffsetDecision() {
		return this.offsetDecision;
	}

	public PaymentStatus getStatus() {
		return this.status;
	}

	public String getGatewayReference() {
		return this.gatewayReference;
	}

	public String getBankReference() {
		return this.bankReference;
	}

	public String getPayeeAccount() {
		return this.payeeAccount;
	}

	public String getHoldReason() {
		return this.holdReason;
	}

	public LocalDate getRequestedOn() {
		return this.requestedOn;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}
}
