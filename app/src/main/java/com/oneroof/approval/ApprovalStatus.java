package com.oneroof.approval;

public enum ApprovalStatus {

	PENDING, APPROVED, REJECTED,

	/** The subject changed before anyone decided, so the request no longer stands. */
	WITHDRAWN

}
