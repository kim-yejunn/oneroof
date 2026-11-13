package com.oneroof.clarification;

/** Something that can put an agency's reply into shape: an AI model, or simple rules as a fallback. */
public interface ReplyInterpreterPort {

	/** A short name for the record, for example the model id. */
	String name();

	/** @throws RuntimeException when no interpretation could be produced */
	ReplyInterpretation interpret(Input input);

	/**
	 * @param kind the request kind, see {@link ClarificationKind}
	 * @param outcome how the agency answered, see {@link ClarificationOutcome}
	 * @param question the request as sent
	 * @param replyText what the agency wrote
	 */
	record Input(ClarificationKind kind, ClarificationOutcome outcome, String question, String replyText) {
	}
}
