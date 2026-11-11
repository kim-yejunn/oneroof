package com.oneroof.rebate;

import java.time.LocalDate;
import java.time.YearMonth;

import com.oneroof.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One month's sales rebate for one agency, as the sales support team recorded it. The
 * {@code original*} fields keep what the source file says happened and are never changed.
 */
@Entity
@Table(name = "rebate_claim")
public class RebateClaim {

	@Id
	@Column(name = "rebate_id")
	private String rebateId;

	@Column(name = "agency_id", nullable = false)
	private String agencyId;

	@Column(name = "period_ym", nullable = false)
	private String periodYm;

	@Column(name = "sales_performance_krw", nullable = false)
	private long salesPerformanceKrw;

	@Column(name = "amount_krw", nullable = false)
	private long amountKrw;

	@Column(name = "original_status", nullable = false)
	private String originalStatus;

	@Column(name = "original_approver")
	private String originalApprover;

	@Column(name = "original_payment_date")
	private LocalDate originalPaymentDate;

	@Column(name = "source_ref", nullable = false)
	private String sourceRef;

	protected RebateClaim() {
	}

	RebateClaim(String rebateId, String agencyId, YearMonth period, long salesPerformanceKrw, long amountKrw,
			String originalStatus, String originalApprover, LocalDate originalPaymentDate, String sourceRef) {
		this.rebateId = rebateId;
		this.agencyId = agencyId;
		this.periodYm = period.toString();
		this.salesPerformanceKrw = salesPerformanceKrw;
		this.amountKrw = amountKrw;
		this.originalStatus = originalStatus;
		this.originalApprover = originalApprover;
		this.originalPaymentDate = originalPaymentDate;
		this.sourceRef = sourceRef;
	}

	public String getRebateId() {
		return this.rebateId;
	}

	public String getAgencyId() {
		return this.agencyId;
	}

	public YearMonth getPeriod() {
		return YearMonth.parse(this.periodYm);
	}

	public Money getSalesPerformance() {
		return Money.ofKrw(this.salesPerformanceKrw);
	}

	public Money getAmount() {
		return Money.ofKrw(this.amountKrw);
	}

	public String getOriginalStatus() {
		return this.originalStatus;
	}

	public String getOriginalApprover() {
		return this.originalApprover;
	}

	public LocalDate getOriginalPaymentDate() {
		return this.originalPaymentDate;
	}

	public String getSourceRef() {
		return this.sourceRef;
	}
}
