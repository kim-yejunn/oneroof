package com.oneroof.shared;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The scenario's notion of "today". The data room is cut off at a fixed date (2026-07-01), so
 * overdue days, limits per month and deadlines must be measured from that date, not from the day
 * the application happens to run.
 */
@Component
public class BusinessClock {

	private final LocalDate businessDate;
	private final ZoneId zone;

	public BusinessClock(@Value("${oneroof.business-date}") LocalDate businessDate,
			@Value("${oneroof.business-zone}") ZoneId zone) {
		this.businessDate = businessDate;
		this.zone = zone;
	}

	/** The fixed business date. */
	public LocalDate today() {
		return this.businessDate;
	}

	/** The business date combined with the current wall-clock time, so events within a day stay ordered. */
	public LocalDateTime now() {
		return LocalDateTime.of(this.businessDate, LocalTime.now(this.zone));
	}

	/** The real instant something was recorded, kept next to the business date for audit purposes. */
	public Instant recordedAt() {
		return Instant.now();
	}

	public ZoneId zone() {
		return this.zone;
	}
}
