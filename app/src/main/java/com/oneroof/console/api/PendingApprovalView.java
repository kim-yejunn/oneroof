package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "역할의 승인 대기 목록 한 줄")
public record PendingApprovalView(long id,
		@Schema(example = "RebateReview", allowableValues = { "RebateReview", "RetrospectiveFinding", "OffsetProposal" }) String subjectType,
		@Schema(example = "38") String subjectId,
		@Schema(allowableValues = { "SALES_SUPPORT", "SALES_EXECUTIVE", "FINANCE" }) String requiredRole,
		String title,
		long amountKrw,
		String status,
		LocalDate requestedOn,
		String correlationId,
		@Schema(nullable = true) Boolean exceedsLimit,
		@Schema(nullable = true) Boolean relatedGroup,
		@Schema(nullable = true) String period) {
}
