package com.oneroof.approval;

/**
 * Asks for a decision from the role that holds the authority. Published by the module that owns
 * the subject; this module knows nothing about what the subject is.
 *
 * @param subjectType the kind of thing to decide on, for example {@code RebateReview}
 * @param subjectId the identifier of that thing
 * @param requiredRole the name of the {@link com.oneroof.shared.Role} with authority
 * @param title a one-line description for the approver's inbox
 * @param rationale the figures and evidence the approver needs
 * @param amountKrw the amount the decision is about
 * @param correlationId the id of the event that started the process
 */
public record ApprovalRequestSubmitted(String subjectType, String subjectId, String requiredRole, String title,
		String rationale, long amountKrw, String correlationId) {
}
