package com.oneroof.intake;

/**
 * A month's rebate settlement was submitted for processing.
 *
 * @param eventId the unique id of the incoming event; later steps use it as the correlation id
 * @param period the month, formatted {@code yyyy-MM}
 */
public record SettlementBatchReceived(String eventId, String period) {
}
