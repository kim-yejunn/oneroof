package com.oneroof.receivables;

/** Why what an agency owes changed after the aging report. */
public enum AdjustmentKind {

	/** A rebate owed to the agency was set off against the receivable. Negative when an offset is reversed. */
	OFFSET,

	/** The agency paid money in (a simulated bank notice). */
	DEPOSIT

}
