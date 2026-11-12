package com.oneroof.intake;

/**
 * An agency answered a clarification request, or its deadline passed without an answer.
 *
 * @param eventId the unique id of the reply
 * @param clarificationId the request answered
 * @param outcome {@code EXPLAINED}, {@code ACCOUNT_CONFIRMED}, {@code ACCOUNT_CHANGED} or {@code NO_REPLY}
 * @param replyText what the agency wrote, empty for {@code NO_REPLY}
 * @param replyAccount the account the agency named, only for {@code ACCOUNT_CHANGED}
 */
public record ClarificationReplyReceived(String eventId, long clarificationId, String outcome, String replyText,
		String replyAccount) {
}
