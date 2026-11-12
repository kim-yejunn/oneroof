package com.oneroof.clarification;

import java.util.List;

/**
 * Another module needs agencies to explain or confirm something before it can go on. One event
 * carries every request raised by the same action.
 */
public record ClarificationsRequested(List<Request> requests) {

	/**
	 * @param kind a {@link ClarificationKind} name
	 * @param subjectType what is waiting for the answer, for example "RebateReview"
	 * @param subjectId its identifier
	 * @param agencyIds comma-separated agencies the request goes to
	 * @param title one line shown in lists
	 * @param question the full request, with the facts it rests on
	 * @param correlationId the event the subject belongs to
	 */
	public record Request(String kind, String subjectType, String subjectId, String agencyIds, String title,
			String question, String correlationId) {
	}
}
