package com.oneroof.clarification;

/** How a clarification request ended. */
public enum ClarificationOutcome {

	/** The agencies explained the shared identifiers and named their evidence. */
	EXPLAINED("소명 제출"),

	/** The agency confirmed the registered account as the payee account. */
	ACCOUNT_CONFIRMED("등록 계좌 확인"),

	/** The agency named a different payee account. */
	ACCOUNT_CHANGED("계좌 변경 회신"),

	/** The agency accepted the offset. */
	OFFSET_ACCEPTED("상계 수용"),

	/** The agency objected to the offset and said why. */
	OFFSET_DISPUTED("상계 이의"),

	/** No answer arrived by the deadline. */
	NO_REPLY("기한 내 무응답");

	private final String label;

	ClarificationOutcome(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}
}
