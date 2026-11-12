package com.oneroof.console.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "한도 단위(관련자 그룹 또는 단독 대리점) 하나의 월 검토")
public record ReviewView(
		@Schema(example = "38") long id,
		@Schema(example = "2026-06") String period,
		@Schema(description = "관련자 그룹 키 또는 대리점 ID", example = "RPG-D-011") String unitKey,
		@Schema(description = "여러 관련 대리점을 합산한 단위인지") boolean relatedGroup,
		@Schema(description = "쉼표로 구분한 구성 대리점", example = "D-011,D-024,D-037") String memberAgencyIds,
		@Schema(description = "한도를 적용한 합산액", example = "3888097") long totalKrw,
		@Schema(example = "2000000") long limitKrw,
		boolean exceedsLimit,
		@Schema(allowableValues = { "SALES_SUPPORT", "SALES_EXECUTIVE" }) String requiredApprover,
		@Schema(allowableValues = { "AWAITING_APPROVAL", "APPROVED", "REJECTED", "PAID", "CLARIFICATION_REQUESTED", "REFERRED_TO_AUDIT", "SUPERSEDED" }) String status,
		String title,
		@Schema(description = "정산을 연 사건 ID. 이 검토에서 생긴 모든 기록을 묶음") String correlationId,
		@Schema(nullable = true) Long approvalRequestId,
		@Schema(nullable = true, allowableValues = { "PENDING", "APPROVED", "REJECTED", "WITHDRAWN" }) String approvalStatus,
		@Schema(nullable = true) String decidedByRole,
		@Schema(nullable = true) String decidedByName,
		@Schema(nullable = true) String decisionComment,
		long claimCount,
		long paidCount,
		@Schema(description = "1차 결정은 1, 소명 첨부 재상신은 2") int round,
		@Schema(nullable = true) Long clarificationId,
		@Schema(nullable = true) String clarificationStatus) {
}
