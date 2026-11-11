package com.oneroof.intake;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An incoming event that has been accepted. Its id is what makes a repeated delivery harmless. */
@Entity
@Table(name = "inbox_event")
public class InboxEvent {

	@Id
	@Column(name = "event_id")
	private String eventId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@Column(nullable = false)
	private String summary;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(name = "business_date", nullable = false)
	private LocalDate businessDate;

	protected InboxEvent() {
	}

	InboxEvent(String eventId, String eventType, String summary, Instant receivedAt, LocalDate businessDate) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.summary = summary;
		this.receivedAt = receivedAt;
		this.businessDate = businessDate;
	}

	public String getEventId() {
		return this.eventId;
	}

	public String getEventType() {
		return this.eventType;
	}

	public String getSummary() {
		return this.summary;
	}

	public Instant getReceivedAt() {
		return this.receivedAt;
	}

	public LocalDate getBusinessDate() {
		return this.businessDate;
	}
}
