package com.oneroof.receivables;

import java.time.LocalDate;

import com.oneroof.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A payment received from an agency. The bank account is the one the money actually came from,
 * which can differ from the account registered in the agency master.
 */
@Entity
@Table(name = "deposit")
public class Deposit {

	@Id
	@Column(name = "deposit_id")
	private String depositId;

	@Column(name = "agency_id", nullable = false)
	private String agencyId;

	@Column(name = "deposit_date", nullable = false)
	private LocalDate depositDate;

	@Column(name = "amount_krw", nullable = false)
	private long amountKrw;

	@Column(name = "bank_account_no", nullable = false)
	private String bankAccountNo;

	@Column(name = "matched_ar_id")
	private String matchedArId;

	@Column(name = "source_ref", nullable = false)
	private String sourceRef;

	protected Deposit() {
	}

	Deposit(String depositId, String agencyId, LocalDate depositDate, long amountKrw, String bankAccountNo,
			String matchedArId, String sourceRef) {
		this.depositId = depositId;
		this.agencyId = agencyId;
		this.depositDate = depositDate;
		this.amountKrw = amountKrw;
		this.bankAccountNo = bankAccountNo;
		this.matchedArId = matchedArId;
		this.sourceRef = sourceRef;
	}

	public String getDepositId() {
		return this.depositId;
	}

	public String getAgencyId() {
		return this.agencyId;
	}

	public LocalDate getDepositDate() {
		return this.depositDate;
	}

	public Money getAmount() {
		return Money.ofKrw(this.amountKrw);
	}

	public String getBankAccountNo() {
		return this.bankAccountNo;
	}

	public String getMatchedArId() {
		return this.matchedArId;
	}

	public String getSourceRef() {
		return this.sourceRef;
	}
}
