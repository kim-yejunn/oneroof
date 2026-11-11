package com.oneroof.payment;

public enum PaymentStatus {

	/** Created after approval; not yet handed to the bank. */
	REQUESTED,

	/** Handed to the bank; waiting for its reply. */
	SENT,

	/** The bank confirmed the transfer. */
	PAID,

	/** The bank reported that the transfer failed. */
	FAILED

}
