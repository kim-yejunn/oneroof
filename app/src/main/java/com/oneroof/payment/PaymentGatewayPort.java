package com.oneroof.payment;

import java.util.List;

/**
 * The outbound connection to the bank. Payments are sent as one bulk transfer per batch, the way
 * corporate banking accepts many transfers at once. The contest allows external execution to be
 * simulated, so the only adapter is a mock; a real banking adapter would implement the same interface.
 */
public interface PaymentGatewayPort {

	/**
	 * Hands a bulk transfer to the bank.
	 * @return the bank gateway's reference for the transfer and for each order, in the order given
	 */
	BulkReceipt sendBulk(List<PaymentOrder> orders);

	/** Whether this adapter performs real transfers or simulates them. */
	boolean isSimulated();

	record PaymentOrder(long instructionId, String agencyId, long amountKrw) {
	}

	record BulkReceipt(String bulkReference, List<String> orderReferences) {
	}
}
