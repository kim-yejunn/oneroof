package com.oneroof.audit;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A matter handed to the audit team. Referral ends this round of review; it does not decide that
 * anything improper happened, and it stays open until audit closes it.
 */
@Entity
@Table(name = "audit_referral")
public class AuditReferral {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "subject_type", nullable = false, updatable = false)
	private String subjectType;

	@Column(name = "subject_id", nullable = false, updatable = false)
	private String subjectId;

	@Column(nullable = false, updatable = false)
	private String title;

	@Column(nullable = false, updatable = false)
	private String reason;

	@Column(name = "referred_by", nullable = false, updatable = false)
	private String referredBy;

	@Column(name = "referred_on", nullable = false, updatable = false)
	private LocalDate referredOn;

	@Column(nullable = false)
	private String status;

	@Column(name = "correlation_id", nullable = false, updatable = false)
	private String correlationId;

	protected AuditReferral() {
	}

	AuditReferral(Referral referral, LocalDate referredOn) {
		this.subjectType = referral.subjectType();
		this.subjectId = referral.subjectId();
		this.title = referral.title();
		this.reason = referral.reason();
		this.referredBy = referral.referredBy();
		this.referredOn = referredOn;
		this.status = "OPEN";
		this.correlationId = referral.correlationId();
	}

	public Long getId() {
		return this.id;
	}

	public String getSubjectType() {
		return this.subjectType;
	}

	public String getSubjectId() {
		return this.subjectId;
	}

	public String getTitle() {
		return this.title;
	}

	public String getReason() {
		return this.reason;
	}

	public String getStatus() {
		return this.status;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

	/**
	 * What is handed over and why.
	 *
	 * @param referredBy the role whose rule or decision caused the referral
	 */
	public record Referral(String subjectType, String subjectId, String title, String reason, String referredBy,
			String correlationId) {
	}
}
