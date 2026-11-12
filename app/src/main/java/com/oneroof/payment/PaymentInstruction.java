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
