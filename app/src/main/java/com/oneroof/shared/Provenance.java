package com.oneroof.shared;

/**
 * Where a piece of information came from. The contest rules require every value shown or used in
 * a decision to be traceable to one of these, and the original files to stay untouched.
 */
public enum Provenance {

	/** A record read from the original data room files. */
	ORIGINAL("원본"),

	/** A forecast, opinion or analysis written by a department; a claim, not a verified fact. */
	DEPARTMENT_CLAIM("부서 주장"),

	/** A rule or value this system assumes because the originals do not define it. */
	ASSUMPTION("가정"),

	/** A simulated reply from an external party, entered during a demonstration. */
	MOCK_REPLY("모의 회신"),

	/** A value this system computed from other values. */
	CALCULATED("계산 결과"),

	/**
	 * Text an AI model wrote from other text, checked against a schema and against the source text,
	 * and shown for a person to review. Never used to compute or decide.
	 */
	AI_GENERATED("AI 생성");

	private final String label;

	Provenance(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}
}
