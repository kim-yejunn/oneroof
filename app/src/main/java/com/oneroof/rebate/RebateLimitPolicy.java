package com.oneroof.rebate;

import java.time.LocalDate;

import com.oneroof.shared.Money;
import com.oneroof.shared.Provenance;
import com.oneroof.shared.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The monthly rebate limit and who may approve on each side of it. A monthly total up to the
 * limit is decided by the sales support team; above it, the sales executive must approve.
 * Related parties are summed before the limit is applied.
 */
@Entity
@Table(name = "rebate_limit_policy")
public class RebateLimitPolicy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "monthly_limit_krw", nullable = false)
	private long monthlyLimitKrw;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Provenance provenance;

	@Column(name = "source_ref", nullable = false)
	private String sourceRef;

	protected RebateLimitPolicy() {
	}

	RebateLimitPolicy(long monthlyLimitKrw, LocalDate effectiveFrom, Provenance provenance, String sourceRef) {
		this.monthlyLimitKrw = monthlyLimitKrw;
		this.effectiveFrom = effectiveFrom;
		this.provenance = provenance;
		this.sourceRef = sourceRef;
	}

	public Money getMonthlyLimit() {
		return Money.ofKrw(this.monthlyLimitKrw);
	}

	public boolean isExceededBy(Money monthlyTotal) {
		return monthlyTotal.isGreaterThan(getMonthlyLimit());
	}

	public Role approverFor(Money monthlyTotal) {
		return isExceededBy(monthlyTotal) ? Role.SALES_EXECUTIVE : Role.SALES_SUPPORT;
	}

	public LocalDate getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public Provenance getProvenance() {
		return this.provenance;
	}

	public String getSourceRef() {
		return this.sourceRef;
	}
}
