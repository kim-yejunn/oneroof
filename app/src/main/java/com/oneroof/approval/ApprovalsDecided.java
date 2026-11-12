package com.oneroof.approval;

import java.util.List;

/**
 * People with authority approved or rejected requests. One user action publishes one event, however
 * many requests it decided, so the subjects' owners can apply the outcome in one transaction.
 *
 * @param decisions every decision made by the action
 */
public record ApprovalsDecided(List<Decision> decisions) {

	/**
	 * @param requestId the approval request
	 * @param subjectType the kind of thing decided on
	 * @param subjectId the identifier of that thing
	 * @param approved {@code true} for approval, {@code false} for rejection
	 * @param decidedByRole the name of the role that decided
	 * @param comment the decider's comment, possibly empty
	 * @param correlationId the id of the event that started the process
	 */
	public record Decision(long requestId, String subjectType, String subjectId, boolean approved, String decidedByRole,
			String comment, String correlationId) {
	}
}
