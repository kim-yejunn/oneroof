package com.oneroof.receivables;

/**
 * What an agency owes went down because it paid money in. Anything waiting on that balance should
 * look at it again.
 *
 * @param agencyId the agency
 * @param depositKrw the amount it paid in
 * @param cause what happened, in words
 * @param correlationId the id of the deposit event
 */
public record OverdueBalanceChanged(String agencyId, long depositKrw, String cause, String correlationId) {
}
