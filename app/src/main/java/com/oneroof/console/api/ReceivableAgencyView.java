package com.oneroof.console.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대리점 하나의 미수금 요약")
public record ReceivableAgencyView(String agencyId,
		String agencyName,
		@Schema(description = "미수금 송장 수") long itemCount,
		@Schema(description = "원본 연령표의 미회수 잔액 합계") long originalOutstandingKrw,
		@Schema(description = "장려금과 상계한 금액 (계산 결과)") long offsetKrw,
		@Schema(description = "모의 입금 반영액") long depositKrw,
		@Schema(description = "지금 남은 잔액") long outstandingKrw,
		@Schema(description = "만기 경과 잔액") long overdueKrw,
		@Schema(description = "상계 기준일수 초과 연체 잔액") long overdueBeyondKrw,
		@Schema(description = "가장 오래된 연체일수 (원본)") int maxAgingDays) {
}
