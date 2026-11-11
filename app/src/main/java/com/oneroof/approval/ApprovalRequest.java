package com.oneroof.approval;

import java.time.LocalDate;

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

/** A decision waiting for, or made by, the role that holds the authority for it. */
@Entity
@Table(name = "approval_request")
public class ApprovalRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "subject_type", nullable = false)
	private String subjectType;

	@Column(name = "subject_id", nullable = false)
	private String subjectId;

	@Enumerated(EnumType.STRING)
	@Column(name = "required_role", nullable = false)
	private Role requiredRole;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String rationale;

	@Column(name = "amount_krw", nullable = false)
	private long amountKrw;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ApprovalStatus status;

	@Column(name = "requested_on", nullable = false)
	private LocalDate requestedOn;

	@Enumerated(EnumType.STRING)
	@Column(name = "decided_by_role")
	private Role decidedByRole;

	@Column(name = "decided_by_name")
	private String decidedByName;

	@Column(name = "decided_on")
	private LocalDate decidedOn;

	@Column(name = "decision_comment")
	private String decisionComment;

	@Column(name = "correlation_id", nullable = false)
	private String correlationId;

	@Version
	private long version;

	protected ApprovalRequest() {
	}

	ApprovalRequest(ApprovalRequestSubmitted submitted, LocalDate requestedOn) {
		this.subjectType = submitted.subjectType();
		this.subjectId = submitted.subjectId();
		this.requiredRole = Role.valueOf(submitted.requiredRole());
		this.title = submitted.title();
		this.rationale = submitted.rationale();
		this.amountKrw = submitted.amountKrw();
		this.status = ApprovalStatus.PENDING;
		this.requestedOn = requestedOn;
		this.correlationId = submitted.correlationId();
	}

	/**
	 * Records the decision. Only the role named on the request may decide, and only once.
	 */
	void decide(Role actorRole, String actorName, boolean approved, String comment, LocalDate decidedOn) {
		if (actorRole != this.requiredRole) {
			throw new AuthorityViolationException("%s 권한 사안이라 %s 역할은 결정할 수 없음 (승인 요청 #%d)"
				.formatted(this.requiredRole.label(), actorRole.label(), this.id));
		}
		if (this.status != ApprovalStatus.PENDING) {
			throw new IllegalStateException("승인 요청 #%d 은 이미 %s 상태임".formatted(this.id, this.status));
		}
		this.status = approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
		this.decidedByRole = actorRole;
		this.decidedByName = actorName;
		this.decidedOn = decidedOn;
		this.decisionComment = comment;
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

	public Role getRequiredRole() {
		return this.requiredRole;
	}

	public String getTitle() {
		return this.title;
	}

	public String getRationale() {
		return this.rationale;
	}

	public Money getAmount() {
		return Money.ofKrw(this.amountKrw);
	}

	public ApprovalStatus getStatus() {
		return this.status;
	}

	public LocalDate getRequestedOn() {
		return this.requestedOn;
	}

	public Role getDecidedByRole() {
		return this.decidedByRole;
	}

	public String getDecidedByName() {
		return this.decidedByName;
	}

	public LocalDate getDecidedOn() {
		return this.decidedOn;
	}

	public String getDecisionComment() {
		return this.decisionComment;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}
}
