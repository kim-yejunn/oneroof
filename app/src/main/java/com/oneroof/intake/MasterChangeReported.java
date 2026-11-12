package com.oneroof.intake;

/**
 * Someone reported that an agency's registered identifier is wrong or has changed.
 *
 * @param eventId the unique id of the report
 * @param agencyId the agency concerned
 * @param kind which identifier: {@code REPRESENTATIVE}, {@code ADDRESS} or {@code BANK_ACCOUNT}
 * @param newValue the value to register
 * @param effectiveFrom ISO date from which the new value applies
 * @param reason why the value changes, as reported
 * @param reportedBy the role that reported it
 */
public record MasterChangeReported(String eventId, String agencyId, String kind, String newValue, String effectiveFrom,
		String reason, String reportedBy) {
}
