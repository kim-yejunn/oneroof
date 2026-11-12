package com.oneroof.payment;

import java.util.List;

/**
 * The bank confirmed that instructions were paid. One bank reply produces one event.
 *
 * @param payments the instructions confirmed as paid
 */
public record PaymentsCompleted(List<Paid> payments) {

	/**
	 * @param instructionId the payment instruction
	 * @param referenceType the kind of obligation that was paid
	 * @param referenceId the identifier of the obligation
	 * @param correlationId the id of the event that started the process
	 */
	public record Paid(long instructionId, String referenceType, String referenceId, String correlationId) {
	}
}
