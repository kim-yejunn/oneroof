package com.oneroof.masterdata;

/**
 * The three identifiers the approval policy uses to decide whether agencies are the same related
 * party: two or more matching identifiers make two agencies related.
 */
public enum IdentityKind {

	REPRESENTATIVE("대표자"), ADDRESS("주소"), BANK_ACCOUNT("정산계좌");

	private final String label;

	IdentityKind(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}
}
