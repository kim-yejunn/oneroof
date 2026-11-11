package com.oneroof.masterdata;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A Corevia sales agency as registered in the agency master. */
@Entity
@Table(name = "agency")
public class Agency {

	@Id
	@Column(name = "agency_id")
	private String agencyId;

	@Column(nullable = false)
	private String name;

	@Column(name = "business_reg_no", nullable = false)
	private String businessRegNo;

	@Column(name = "rebate_eligible", nullable = false)
	private boolean rebateEligible;

	@Column(name = "registered_date", nullable = false)
	private LocalDate registeredDate;

	@Column(name = "source_ref", nullable = false)
	private String sourceRef;

	protected Agency() {
	}

	Agency(String agencyId, String name, String businessRegNo, boolean rebateEligible, LocalDate registeredDate,
			String sourceRef) {
		this.agencyId = agencyId;
		this.name = name;
		this.businessRegNo = businessRegNo;
		this.rebateEligible = rebateEligible;
		this.registeredDate = registeredDate;
		this.sourceRef = sourceRef;
	}

	public String getAgencyId() {
		return this.agencyId;
	}

	public String getName() {
		return this.name;
	}

	public String getBusinessRegNo() {
		return this.businessRegNo;
	}

	public boolean isRebateEligible() {
		return this.rebateEligible;
	}

	public LocalDate getRegisteredDate() {
		return this.registeredDate;
	}

	public String getSourceRef() {
		return this.sourceRef;
	}
}
