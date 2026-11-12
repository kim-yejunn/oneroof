package com.oneroof.rebate;

public enum FindingStatus {

	/** Waiting for the executive to confirm the month after the fact. */
	AWAITING_CONFIRMATION,

	/** The executive confirmed the payments made under the sales support team's authority. */
	CONFIRMED,

	/** The executive did not confirm; audit follows up. */
	REFERRED_TO_AUDIT

}
