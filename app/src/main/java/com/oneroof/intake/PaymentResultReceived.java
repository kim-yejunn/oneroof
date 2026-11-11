package com.oneroof.intake;

/**
 * The bank reported the outcome of a payment instruction.
 *
 * @param eventId the unique id of the incoming event
 * @param instructionId the payment instruction the reply is about
 * @param succeeded whether the transfer went through
 * @param bankReference the bank's own reference for the transfer
 */
public record PaymentResultReceived(String eventId, long instructionId, boolean succeeded, String bankReference) {
}
