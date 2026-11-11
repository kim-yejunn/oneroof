package com.oneroof.audit;

import java.time.Instant;
import java.time.LocalDate;

import com.oneroof.shared.Provenance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@Column(name = "business_date", nullable = false, updatable = false)
	private LocalDate businessDate;

	@Column(name = "actor_role", nullable = false, updatable = false)
	private String actorRole;

	@Column(nullable = false, updatable = false)
	private String action;

	@Column(name = "subject_type", nullable = false, updatable = false)
	private String subjectType;

	@Column(name = "subject_id", nullable = false, updatable = false)
	private String subjectId;

	@Column(nullable = false, updatable = false, columnDefinition = "text")
	private String detail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Provenance provenance;

	@Column(name = "correlation_id", updatable = false)
	private String correlationId;

	protected AuditEntry() {
	}

	AuditEntry(AuditRecord record, Instant recordedAt, LocalDate businessDate) {
		this.recordedAt = recordedAt;
		this.businessDate = businessDate;
		this.actorRole = record.actorRole();
		this.action = record.action();
		this.subjectType = record.subjectType();
		this.subjectId = record.subjectId();
		this.detail = record.detail();
		this.provenance = record.provenance();
		this.correlationId = record.correlationId();
	}

	public Long getId() {
		return this.id;
	}

	public Instant getRecordedAt() {
		return this.recordedAt;
	}

	public LocalDate getBusinessDate() {
		return this.businessDate;
	}

	public String getActorRole() {
		return this.actorRole;
	}

	public String getAction() {
		return this.action;
	}

	public String getSubjectType() {
		return this.subjectType;
	}

	public String getSubjectId() {
		return this.subjectId;
	}

	public String getDetail() {
		return this.detail;
	}

	public Provenance getProvenance() {
		return this.provenance;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}
}
