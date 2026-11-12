package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검토에 대한 승인 요청 한 건")
public record ApprovalHistoryView(long id, String title, String requiredRole,
		@Schema(allowableValues = { "PENDING", "APPROVED", "REJECTED", "WITHDRAWN" }) String status,
		LocalDate requestedOn, @Schema(nullable = true) String decidedByRole,
		@Schema(nullable = true) String decidedByName, @Schema(nullable = true) LocalDate decidedOn,
		@Schema(nullable = true) String decisionComment) {
}
