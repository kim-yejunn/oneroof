package com.oneroof.approval;

import java.util.List;

/**
 * The subjects of these requests changed before anyone decided, for example because a master
 * correction regrouped the agencies. Pending requests about them are withdrawn.
 */
public record ApprovalRequestsWithdrawn(List<Withdrawal> withdrawals) {

	public record Withdrawal(String subjectType, String subjectId, String reason, String correlationId) {
	}
}
