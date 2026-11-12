package com.oneroof.intake;

/**
 * An agency paid money in after the aging report, as reported during a demonstration (a simulated
 * bank notice). It reduces what the agency owes, oldest overdue invoice first.
 *
 * @param eventId the unique id of the report
 * @param agencyId the agency that paid
 * @param amountKrw the amount received
 * @param bankAccount the account the money came from
 * @param reportedBy the role that entered it
 */
public record AgencyDepositReported(String eventId, String agencyId, long amountKrw, String bankAccount,
		String reportedBy) {
}
