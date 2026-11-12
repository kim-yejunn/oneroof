package com.oneroof.payment;

public enum PaymentStatus {

	/** The agency has receivables overdue long enough to be offset; finance decides first. */
	OFFSET_REVIEW,

	/** The whole amount was set off against overdue receivables; nothing goes to the bank. */
	OFFSET_SETTLED,

	/** The agency disputed a full offset; finance decides whether to keep or cancel it. */
	OFFSET_DISPUTED,

	/** Created after approval; not yet handed to the bank. */
	REQUESTED,

	/** Not sent: the payee account must be confirmed first. */
	HELD,

	/** Handed to the bank; waiting for its reply. */
	SENT,

	/** The bank confirmed the transfer. */
	PAID,

	/** The bank reported that the transfer failed. */
	FAILED

}
