package com.oneroof.masterdata;

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

/** A correction applied to the agency master: what it replaced, from when, why, and who said so. */
@Entity
@Table(name = "master_change")
public class MasterChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, updatable = false)
	private String eventId;

	@Column(name = "agency_id", nullable = false, updatable = false)
	private String agencyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private IdentityKind kind;

	@Column(name = "previous_value", nullable = false, updatable = false)
	private String previousValue;

	@Column(name = "new_value", nullable = false, updatable = false)
	private String newValue;

	@Column(name = "effective_from", nullable = false, updatable = false)
	private LocalDate effectiveFrom;

	@Column(nullable = false, updatable = false)
	private String reason;

	@Column(name = "reported_by", nullable = false, updatable = false)
	private String reportedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Provenance provenance;

	@Column(name = "recorded_on", nullable = false, updatable = false)
	private LocalDate recordedOn;

	protected MasterChange() {
	}

	MasterChange(String eventId, String agencyId, IdentityKind kind, String previousValue, String newValue,
			LocalDate effectiveFrom, String reason, String reportedBy, Provenance provenance, LocalDate recordedOn) {
		this.eventId = eventId;
		this.agencyId = agencyId;
		this.kind = kind;
		this.previousValue = previousValue;
		this.newValue = newValue;
		this.effectiveFrom = effectiveFrom;
		this.reason = reason;
		this.reportedBy = reportedBy;
		this.provenance = provenance;
		this.recordedOn = recordedOn;
	}

	public Long getId() {
		return this.id;
	}

	public String getAgencyId() {
		return this.agencyId;
	}

	public IdentityKind getKind() {
		return this.kind;
	}

	public String getPreviousValue() {
		return this.previousValue;
	}

	public String getNewValue() {
		return this.newValue;
	}

	public LocalDate getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public String getReason() {
		return this.reason;
	}

	public Provenance getProvenance() {
		return this.provenance;
	}
}
