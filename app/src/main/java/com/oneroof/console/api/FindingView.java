package com.oneroof.console.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마스터 정정 뒤 다시 계산해 보니 임원 승인 대상이었던 전결 처리 월. 지급은 되돌리지 않고 사후 확인합니다.")
public record FindingView(long id,
		@Schema(example = "2026-01") String period,
		@Schema(example = "RPG-D-034") String unitKey,
		String memberAgencyIds,
		@Schema(description = "전결로 처리된 장려금") String rebateIds,
		long totalKrw,
		long limitKrw,
		@Schema(description = "당시 승인한 주체", example = "영업지원팀") String approvedBy,
		@Schema(allowableValues = { "SALES_EXECUTIVE" }) String requiredApprover,
		@Schema(allowableValues = { "AWAITING_CONFIRMATION", "CONFIRMED", "REFERRED_TO_AUDIT" }) String status,
		long masterChangeId,
		String title,
		String rationale,
		String correlationId,
		@Schema(nullable = true) Long approvalRequestId,
		@Schema(nullable = true) String approvalStatus,
		@Schema(nullable = true) String decisionComment) {
}
