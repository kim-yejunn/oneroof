package com.oneroof.rebate;

/** Where a rebate claim stands in settlement. */
public enum ClaimStatus {

	/** The source file records it as approved and paid before this system took over. */
	SETTLED_IN_SOURCE,

	/** The source file records it as rejected. */
	REJECTED_IN_SOURCE,

	/** Unpaid and not yet submitted for settlement. */
	PENDING,

	/** Part of a review that is waiting for a decision. */
	UNDER_APPROVAL,

	/** Approved; a payment instruction has been requested. */
	PAYMENT_REQUESTED,

	/** The bank confirmed payment. */
	PAID,

	/** The review it belongs to was rejected. */
	REJECTED

}
