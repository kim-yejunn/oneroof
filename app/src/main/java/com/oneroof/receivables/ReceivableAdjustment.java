package com.oneroof.receivables;

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
 * A change to one receivable after the aging report. The original row is never touched; what is
 * still owed is the original outstanding amount less every adjustment.
 */
@Entity
@Table(name = "receivable_adjustment")
public class ReceivableAdjustment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ar_id", nullable = false)
	private String arId;

	@Column(name = "agency_id", nullable = false)
	private String agencyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AdjustmentKind kind;

	@Column(name = "amount_krw", nullable = false)
	private long amountKrw;

	/** What caused it: {@code PaymentInstruction#12}, its reversal, or the deposit event id. */
	@Column(nullable = false)
	private String reference;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Provenance provenance;

	@Column(name = "recorded_on", nullable = false)
	private LocalDate recordedOn;

	@Column(name = "correlation_id", nullable = false)
	private String correlationId;

	protected ReceivableAdjustment() {
	}

	ReceivableAdjustment(String arId, String agencyId, AdjustmentKind kind, long amountKrw, String reference,
			Provenance provenance, LocalDate recordedOn, String correlationId) {
		this.arId = arId;
		this.agencyId = agencyId;
		this.kind = kind;
		this.amountKrw = amountKrw;
		this.reference = reference;
		this.provenance = provenance;
		this.recordedOn = recordedOn;
		this.correlationId = correlationId;
	}

	public String getArId() {
		return this.arId;
	}

	public String getAgencyId() {
		return this.agencyId;
	}

	public AdjustmentKind getKind() {
		return this.kind;
	}

	public long getAmountKrw() {
		return this.amountKrw;
	}

	public String getReference() {
		return this.reference;
	}
}
