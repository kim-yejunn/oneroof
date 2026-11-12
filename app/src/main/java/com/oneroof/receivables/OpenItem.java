package com.oneroof.receivables;

/**
 * A receivable with what is still owed on it after every adjustment.
 *
 * @param arId the invoice
 * @param agingDays days past due on the aging report date (original)
 * @param outstandingKrw still owed now
 * @param sourceRef the aging report row
 */
public record OpenItem(String arId, int agingDays, long outstandingKrw, String sourceRef) {
}
