package com.oneroof.payment;

/**
 * The bank confirmed that an instruction was paid.
 *
 * @param instructionId the payment instruction
 * @param referenceType the kind of obligation that was paid
 * @param referenceId the identifier of the obligation
 * @param correlationId the id of the event that started the process
 */
public record PaymentCompleted(long instructionId, String referenceType, String referenceId, String correlationId) {
}
