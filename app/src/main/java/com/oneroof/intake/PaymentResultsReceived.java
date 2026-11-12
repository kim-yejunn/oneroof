package com.oneroof.intake;

import java.util.List;

/**
 * The bank reported the outcome of payment instructions, as one reply covering one or many transfers.
 *
 * @param eventId the unique id of the incoming reply
 * @param results the outcome of each instruction
 */
public record PaymentResultsReceived(String eventId, List<Result> results) {

	/**
	 * @param instructionId the payment instruction the result is about
	 * @param succeeded whether the transfer went through
	 * @param bankReference the bank's own reference for the transfer
	 */
	public record Result(long instructionId, boolean succeeded, String bankReference) {
	}
}
