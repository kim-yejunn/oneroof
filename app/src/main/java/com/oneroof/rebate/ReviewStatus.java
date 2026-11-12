package com.oneroof.rebate;

public enum ReviewStatus {

	AWAITING_APPROVAL, APPROVED, REJECTED, PAID,

	/** The executive rejected a related group; the agencies were asked to explain before a second decision. */
	CLARIFICATION_REQUESTED,

	/** No explanation came, or the executive rejected again after one. Payment waits for audit. */
	REFERRED_TO_AUDIT,

	/** A master correction changed the limit unit before anyone decided; a new review replaced this one. */
	SUPERSEDED

}
