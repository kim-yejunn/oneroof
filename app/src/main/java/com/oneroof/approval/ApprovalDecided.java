package com.oneroof.approval;

/**
 * A person with authority approved or rejected a request.
 *
 * @param requestId the approval request
 * @param subjectType the kind of thing decided on
 * @param subjectId the identifier of that thing
 * @param approved {@code true} for approval, {@code false} for rejection
 * @param decidedByRole the name of the role that decided
 * @param comment the decider's comment, possibly empty
 * @param correlationId the id of the event that started the process
 */
public record ApprovalDecided(long requestId, String subjectType, String subjectId, boolean approved,
		String decidedByRole, String comment, String correlationId) {
}
