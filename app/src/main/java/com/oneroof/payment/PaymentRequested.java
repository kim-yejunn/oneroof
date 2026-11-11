package com.oneroof.payment;

/**
 * Asks for an approved amount to be paid to an agency. Published by the module that owns the
 * obligation; this module does not know why the money is owed.
 *
 * @param referenceType the kind of obligation, for example {@code RebateClaim}
 * @param referenceId the identifier of the obligation; one instruction is created per reference
 * @param agencyId the agency to pay
 * @param amountKrw the amount to pay
 * @param correlationId the id of the event that started the process
 */
public record PaymentRequested(String referenceType, String referenceId, String agencyId, long amountKrw,
		String correlationId) {
}
