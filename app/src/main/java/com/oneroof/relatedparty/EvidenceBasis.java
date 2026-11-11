package com.oneroof.relatedparty;

/** How an identifier became known for an agency. */
public enum EvidenceBasis {

	/** Registered in the agency master. */
	MASTER_REGISTRATION("마스터 등록"),

	/**
	 * The bank account a deposit actually came from. The data dictionary requires comparing it as
	 * well, because the account in use can differ from the registered one.
	 */
	OBSERVED_DEPOSIT("실제 입금 계좌");

	private final String label;

	EvidenceBasis(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}
}
