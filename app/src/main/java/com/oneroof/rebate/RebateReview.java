package com.oneroof.rebate;

import java.time.YearMonth;
import java.util.List;

import com.oneroof.shared.Money;
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
 * The decision unit for one month: everything due to a single agency, or to a related group taken
 * together. It records the total the limit was applied to and the role that had to decide.
 */
@Entity
@Table(name = "rebate_review")
public class RebateReview {

	public static final String SUBJECT_TYPE = "RebateReview";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "batch_id", nullable = false)
	private Long batchId;

	@Column(name = "unit_key", nullable = false)
	private String unitKey;

	@Column(name = "period_ym", nullable = false)
	private String periodYm;

	@Column(name = "related_group", nullable = false)
	private boolean relatedGroup;

	@Column(name = "member_agency_ids", nullable = false)
	private String memberAgencyIds;

	@Column(name = "total_krw", nullable = false)
	private long totalKrw;

	@Column(name = "limit_krw", nullable = false)
	private long limitKrw;

	@Column(name = "exceeds_limit", nullable = false)
	private boolean exceedsLimit;

	@Enumerated(EnumType.STRING)
	@Column(name = "required_approver", nullable = false)
	private Role requiredApprover;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReviewStatus status;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String rationale;

	@Column(name = "correlation_id", nullable = false)
	private String correlationId;

	@Version
	private long version;

	protected RebateReview() {
	}

	RebateReview(long batchId, ExposureUnit unit, String title, String rationale, String correlationId) {
		this.batchId = batchId;
		this.unitKey = unit.unitKey();
		this.periodYm = unit.period().toString();
		this.relatedGroup = unit.relatedGroup();
		this.memberAgencyIds = String.join(",", unit.agencyIds());
		this.totalKrw = unit.total().krw();
		this.limitKrw = unit.limit().krw();
		this.exceedsLimit = unit.exceedsLimit();
		this.requiredApprover = unit.requiredApprover();
		this.status = ReviewStatus.AWAITING_APPROVAL;
		this.title = title;
		this.rationale = rationale;
		this.correlationId = correlationId;
	}

	void approve() {
		requireStatus(ReviewStatus.AWAITING_APPROVAL);
		this.status = ReviewStatus.APPROVED;
	}

	void reject() {
		requireStatus(ReviewStatus.AWAITING_APPROVAL);
		this.status = ReviewStatus.REJECTED;
	}

	void markPaid() {
		requireStatus(ReviewStatus.APPROVED);
		this.status = ReviewStatus.PAID;
	}

	private void requireStatus(ReviewStatus expected) {
		if (this.status != expected) {
			throw new IllegalStateException(
					"검토 #%d 은 %s 상태라 처리할 수 없음 (필요 상태 %s)".formatted(this.id, this.status, expected));
		}
	}

	public Long getId() {
		return this.id;
	}

	public Long getBatchId() {
		return this.batchId;
	}

	public String getUnitKey() {
		return this.unitKey;
	}

	public YearMonth getPeriod() {
		return YearMonth.parse(this.periodYm);
	}

	public boolean isRelatedGroup() {
		return this.relatedGroup;
	}

	public List<String> getMemberAgencyIds() {
		return List.of(this.memberAgencyIds.split(","));
	}

	public Money getTotal() {
		return Money.ofKrw(this.totalKrw);
	}

	public Money getLimit() {
		return Money.ofKrw(this.limitKrw);
	}

	public boolean isExceedsLimit() {
		return this.exceedsLimit;
	}

	public Role getRequiredApprover() {
		return this.requiredApprover;
	}

	public ReviewStatus getStatus() {
		return this.status;
	}

	public String getTitle() {
		return this.title;
	}

	public String getRationale() {
		return this.rationale;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}
}
