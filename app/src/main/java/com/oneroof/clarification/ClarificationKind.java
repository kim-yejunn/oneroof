package com.oneroof.clarification;

import java.util.Set;

/** What an agency is asked to clarify, and which answers make sense for it. */
public enum ClarificationKind {

	/** Why agencies share a representative, address or account; asked before the executive decides again. */
	RELATION_EXPLANATION("관계 소명", Set.of(ClarificationOutcome.EXPLAINED, ClarificationOutcome.NO_REPLY)),

	/** Which account the rebate should be paid to, when deposits came from a different account. */
	ACCOUNT_CONFIRMATION("지급 계좌 확인", Set.of(ClarificationOutcome.ACCOUNT_CONFIRMED,
			ClarificationOutcome.ACCOUNT_CHANGED, ClarificationOutcome.NO_REPLY));

	private final String label;
	private final Set<ClarificationOutcome> outcomes;

	ClarificationKind(String label, Set<ClarificationOutcome> outcomes) {
		this.label = label;
		this.outcomes = outcomes;
	}

	public String label() {
		return this.label;
	}

	public boolean accepts(ClarificationOutcome outcome) {
		return this.outcomes.contains(outcome);
	}
}
