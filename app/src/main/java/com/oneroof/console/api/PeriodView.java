package com.oneroof.console.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "한 달치 판매장려금의 정산 진행 상황")
public record PeriodView(
		@Schema(example = "2026-06") String period,
		@Schema(description = "그 달 장려금 건수", example = "40") long claimCount,
		@Schema(description = "그 달 장려금 합계", example = "37159812") long totalKrw,
		@Schema(description = "정산 접수 전 미지급 건수", example = "40") long pendingCount,
		@Schema(description = "승인 대기 건수") long underApprovalCount,
		@Schema(description = "승인 후 지급 진행 건수") long paymentRequestedCount,
		@Schema(description = "이 시스템에서 지급 완료된 건수") long paidCount,
		@Schema(description = "원본 기록상 이미 지급된 건수") long settledInSourceCount,
		@Schema(description = "원본 또는 이 시스템에서 반려된 건수") long rejectedCount,
		@Schema(description = "이 시스템에서 정산이 접수됐는지") boolean batchOpened,
		@Schema(description = "정산을 연 사건 ID", nullable = true, example = "EVT-SETTLEMENT-2026-06") String sourceEventId) {
}
