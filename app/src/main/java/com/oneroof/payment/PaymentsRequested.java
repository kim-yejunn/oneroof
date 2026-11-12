package com.oneroof.payment;

import java.util.List;

/**
 * Asks for approved amounts to be paid, as one batch. Published by the module that owns the
 * obligations; this module does not know why the money is owed. One instruction is created per
 * reference, and asking again for a reference already instructed does nothing.
 *
 * @param payments the payments to make
 */
public record PaymentsRequested(List<Payment> payments) {

	/**
	 * @param referenceType the kind of obligation, for example {@code RebateClaim}
	 * @param referenceId the identifier of the obligation
	 * @param agencyId the agency to pay
	 * @param amountKrw the amount to pay
	 * @param correlationId the id of the event that started the process
	 */
	public record Payment(String referenceType, String referenceId, String agencyId, long amountKrw,
			String correlationId) {
	}
}
