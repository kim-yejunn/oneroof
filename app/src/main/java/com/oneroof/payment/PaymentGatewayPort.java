package com.oneroof.payment;

/**
 * The outbound connection to the bank. The contest allows external execution to be simulated, so
 * the only adapter is a mock; a real banking adapter would implement the same interface.
 */
public interface PaymentGatewayPort {

	/**
	 * Hands a payment order to the bank.
	 * @return the bank gateway's reference for the order
	 */
	String send(PaymentOrder order);

	/** Whether this adapter performs real transfers or simulates them. */
	boolean isSimulated();

	record PaymentOrder(long instructionId, String agencyId, long amountKrw) {
	}
}
