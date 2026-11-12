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

/**
 * One identifier of an agency during a period. A correction never overwrites a value: it closes
 * the current row and opens a new one, so past approvals can be re-examined against what was
 * registered at the time.
 */
@Entity
@Table(name = "agency_identity")
public class AgencyIdentity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "agency_id", nullable = false)
	private String agencyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IdentityKind kind;

	@Column(name = "identity_value", nullable = false)
	private String value;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	/** Exclusive end; {@code null} while the value is current. */
	@Column(name = "effective_to")
	private LocalDate effectiveTo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Provenance provenance;

	@Column(name = "source_ref", nullable = false)
	private String sourceRef;

	protected AgencyIdentity() {
	}

	AgencyIdentity(String agencyId, IdentityKind kind, String value, LocalDate effectiveFrom, Provenance provenance,
			String sourceRef) {
		this.agencyId = agencyId;
		this.kind = kind;
		this.value = value;
		this.effectiveFrom = effectiveFrom;
		this.provenance = provenance;
		this.sourceRef = sourceRef;
	}

	/** Ends this value the day the replacing value takes effect. */
	void closeOn(LocalDate replacedFrom) {
		if (this.effectiveTo != null) {
			throw new IllegalStateException("이미 종료된 식별정보 #%d".formatted(this.id));
		}
		this.effectiveTo = replacedFrom;
	}

	boolean isEffectiveOn(LocalDate date) {
		return !date.isBefore(this.effectiveFrom) && (this.effectiveTo == null || date.isBefore(this.effectiveTo));
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

	public String getValue() {
		return this.value;
	}

	public LocalDate getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public LocalDate getEffectiveTo() {
		return this.effectiveTo;
	}

	public Provenance getProvenance() {
		return this.provenance;
	}

	public String getSourceRef() {
		return this.sourceRef;
	}
}
