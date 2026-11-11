package com.oneroof.receivables;

import java.time.LocalDate;

import com.oneroof.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An invoice that is not fully collected, from the accounts receivable aging report. */
@Entity
@Table(name = "receivable")
public class Receivable {

	@Id
	@Column(name = "ar_id")
	private String arId;

	/** Set when the debtor is an agency; {@code null} for direct customers. */
	@Column(name = "agency_id")
	private String agencyId;

	/** Set when the debtor is a direct customer; {@code null} for agencies. */
	@Column(name = "customer_id")
	private String customerId;

	@Column(name = "invoice_date", nullable = false)
	private LocalDate invoiceDate;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(name = "amount_krw", nullable = false)
	private long amountKrw;

	@Column(name = "outstanding_krw", nullable = false)
	private long outstandingKrw;

	/** Days past due as the aging report states them on the cut-off date. */
	@Column(name = "aging_days", nullable = false)
	private int agingDays;

	@Column(name = "source_ref", nullable = false)
	private String sourceRef;

	protected Receivable() {
	}

	Receivable(String arId, String agencyId, String customerId, LocalDate invoiceDate, LocalDate dueDate,
			long amountKrw, long outstandingKrw, int agingDays, String sourceRef) {
		this.arId = arId;
		this.agencyId = agencyId;
		this.customerId = customerId;
		this.invoiceDate = invoiceDate;
		this.dueDate = dueDate;
		this.amountKrw = amountKrw;
		this.outstandingKrw = outstandingKrw;
		this.agingDays = agingDays;
		this.sourceRef = sourceRef;
	}

	public String getArId() {
		return this.arId;
	}

	public String getAgencyId() {
		return this.agencyId;
	}

	public String getCustomerId() {
		return this.customerId;
	}

	public LocalDate getInvoiceDate() {
		return this.invoiceDate;
	}

	public LocalDate getDueDate() {
		return this.dueDate;
	}

	public Money getAmount() {
		return Money.ofKrw(this.amountKrw);
	}

	public Money getOutstanding() {
		return Money.ofKrw(this.outstandingKrw);
	}

	public int getAgingDays() {
		return this.agingDays;
	}

	public String getSourceRef() {
		return this.sourceRef;
	}
}
