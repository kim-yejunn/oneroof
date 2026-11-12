package com.oneroof.clarification;

/**
 * A clarification request was answered or ran out of time. The module waiting on the subject
 * decides what the answer means for it.
 *
 * @param clarificationId the request
 * @param kind a {@link ClarificationKind} name
 * @param subjectType what was waiting
 * @param subjectId its identifier
 * @param agencyIds comma-separated agencies asked
 * @param outcome a {@link ClarificationOutcome} name
 * @param replyText what the agencies wrote, empty when they did not answer
 * @param replyAccount the payee account named, only for {@code ACCOUNT_CHANGED}
 * @param correlationId the event the subject belongs to
 */
public record ClarificationClosed(long clarificationId, String kind, String subjectType, String subjectId,
		String agencyIds, String outcome, String replyText, String replyAccount, String correlationId) {
}
