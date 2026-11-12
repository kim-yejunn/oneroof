package com.oneroof.approval;

import java.util.List;

/**
 * Asks for decisions from the roles that hold the authority, as one batch. Published by the module
 * that owns the subjects; this module knows nothing about what the subjects are. A batch is stored
 * in one transaction.
 *
 * @param requests the decisions asked for
 */
public record ApprovalRequestsSubmitted(List<Request> requests) {

	/**
	 * @param subjectType the kind of thing to decide on, for example {@code RebateReview}
	 * @param subjectId the identifier of that thing
	 * @param requiredRole the name of the {@link com.oneroof.shared.Role} with authority
	 * @param title a one-line description for the approver's inbox
	 * @param rationale the figures and evidence the approver needs
	 * @param amountKrw the amount the decision is about
	 * @param correlationId the id of the event that started the process
	 */
	public record Request(String subjectType, String subjectId, String requiredRole, String title, String rationale,
			long amountKrw, String correlationId) {
	}
}
