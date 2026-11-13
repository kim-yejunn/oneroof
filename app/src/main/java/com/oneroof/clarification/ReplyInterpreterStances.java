package com.oneroof.clarification;

import java.util.List;

/** The stances an interpretation may take for each request kind, for adapters to tell the model. */
public final class ReplyInterpreterStances {

	private ReplyInterpreterStances() {
	}

	public static List<String> allowed(ClarificationKind kind) {
		return ReplyInterpreter.STANCES.get(kind).stream().sorted().toList();
	}
}
