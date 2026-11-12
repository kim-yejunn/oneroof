package com.oneroof.receivables;

import java.util.List;

/**
 * Offsets were cancelled, for example after agencies disputed them; the receivables they reduced
 * are owed again. One decision produces one event.
 *
 * @param reversals the offsets cancelled
 */
public record ReceivableOffsetsReversed(List<Reversal> reversals) {

	/**
	 * @param reference the reference the offset was applied under
	 * @param reason why it was cancelled
	 * @param correlationId the id of the event that started the process
	 */
	public record Reversal(String reference, String reason, String correlationId) {
	}
}
