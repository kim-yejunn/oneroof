package com.oneroof.receivables;

import java.util.List;

/**
 * Finance decided to set rebates off against agencies' overdue receivables, as one batch for one
 * decision. Each amount is applied to that agency's invoices overdue by more than
 * {@code overdueDaysOver} days, most overdue first. Applying the same reference twice does nothing.
 *
 * @param offsets the offsets decided
 */
public record ReceivableOffsetsRequested(List<Offset> offsets) {

	/**
	 * @param reference what is set off, for example {@code PaymentInstruction#12}
	 * @param agencyId the agency that is both owed the rebate and owes the receivables
	 * @param amountKrw the amount set off
	 * @param overdueDaysOver only invoices overdue by more days than this are offset
	 * @param correlationId the id of the event that started the process
	 */
	public record Offset(String reference, String agencyId, long amountKrw, int overdueDaysOver,
			String correlationId) {
	}
}
