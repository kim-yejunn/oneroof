package com.oneroof.rebate;

import java.util.List;

import com.oneroof.shared.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A month that was settled under the sales support team's authority, which a master correction
 * shows was over the limit for a related group and so needed the executive. Nothing paid is
 * reversed; the executive confirms after the fact, or the month goes to audit.
 */
@Entity
@Table(name = "retrospective_finding")
public class RetrospectiveFinding {

	public static final String SUBJECT_TYPE = "RetrospectiveFinding";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "period_ym", nullable = false)
	private String periodYm;

	@Column(name = "unit_key", nullable = false)
	private String unitKey;

	@Column(name = "member_agency_ids", nullable = false)
	private String memberAgencyIds;

	@Column(name = "rebate_ids", nullable = false)
	private String rebateIds;

	@Column(name = "total_krw", nullable = false)
	private long totalKrw;

	@Column(name = "limit_krw", nullable = false)
	private long limitKrw;

	@Column(name = "approved_by", nullable = false)
	private String approvedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "required_approver", nullable = false)
	private Role requiredApprover;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FindingStatus status;

	@Column(name = "master_change_id", nullable = false)
	private long masterChangeId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String rationale;

	@Column(name = "correlation_id", nullable = false)
	private String correlationId;

	@Version
	private long version;

	protected RetrospectiveFinding() {
	}

	RetrospectiveFinding(ExposureUnit unit, List<RebateClaim> settled, String approvedBy, long masterChangeId,
			String title, String rationale, String correlationId) {
		this.periodYm = unit.period().toString();
		this.unitKey = unit.unitKey();
		this.memberAgencyIds = String.join(",", unit.agencyIds());
		this.rebateIds = String.join(",", settled.stream().map(RebateClaim::getRebateId).toList());
		this.totalKrw = unit.total().krw();
		this.limitKrw = unit.limit().krw();
		this.approvedBy = approvedBy;
		this.requiredApprover = unit.requiredApprover();
		this.status = FindingStatus.AWAITING_CONFIRMATION;
		this.masterChangeId = masterChangeId;
		this.title = title;
		this.rationale = rationale;
		this.correlationId = correlationId;
	}

	void confirm() {
		requireAwaiting();
		this.status = FindingStatus.CONFIRMED;
	}

	void referToAudit() {
		requireAwaiting();
		this.status = FindingStatus.REFERRED_TO_AUDIT;
	}

	private void requireAwaiting() {
		if (this.status != FindingStatus.AWAITING_CONFIRMATION) {
			throw new IllegalStateException("소급 발견 #%d 은 이미 %s 상태임".formatted(this.id, this.status));
		}
	}

	public Long getId() {
		return this.id;
	}

	public String getPeriodYm() {
		return this.periodYm;
	}

	public String getUnitKey() {
		return this.unitKey;
	}

	public long getTotalKrw() {
		return this.totalKrw;
	}

	public FindingStatus getStatus() {
		return this.status;
	}

	public String getTitle() {
		return this.title;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}
}
