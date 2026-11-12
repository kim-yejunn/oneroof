package com.oneroof.shared;

/** The parties that act in the rebate control process. Authority limits are expressed in these roles. */
public enum Role {

	SYSTEM("시스템"), SALES_SUPPORT("영업지원팀"), SALES_EXECUTIVE("영업임원"), FINANCE("회계·자금팀");

	private final String label;

	Role(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}
}
