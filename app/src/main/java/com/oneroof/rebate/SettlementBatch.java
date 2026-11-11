package com.oneroof.rebate;

import java.time.LocalDate;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One month's rebates taken into settlement. A month is settled once. */
@Entity
@Table(name = "settlement_batch")
public class SettlementBatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "period_ym", nullable = false, unique = true)
	private String periodYm;

	@Column(name = "source_event_id", nullable = false)
	private String sourceEventId;

	@Column(name = "opened_on", nullable = false)
	private LocalDate openedOn;

	protected SettlementBatch() {
	}

	SettlementBatch(YearMonth period, String sourceEventId, LocalDate openedOn) {
		this.periodYm = period.toString();
		this.sourceEventId = sourceEventId;
		this.openedOn = openedOn;
	}

	public Long getId() {
		return this.id;
	}

	public YearMonth getPeriod() {
		return YearMonth.parse(this.periodYm);
	}

	public String getSourceEventId() {
		return this.sourceEventId;
	}

	public LocalDate getOpenedOn() {
		return this.openedOn;
	}
}
