package com.oneroof.console.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "한 달 장려금의 상계 확장 켬·끔 비교")
public record OffsetComparisonView(String period,
		@Schema(description = "확장이 켜져 있는지") boolean enabled,
		@Schema(description = "이 일수를 넘긴 연체만 상계 대상 (교육용 가정)") int overdueDaysOver,
		@Schema(description = "비교 대상 장려금 건수 (원본 반려 제외)") int claimCount,
		@Schema(description = "장려금 합계. 확장을 끄면 전액 현금 지급") long totalKrw,
		@Schema(description = "원본 연령표만으로 계산한 상계액. 입금·결정과 무관하게 고정") long originalOffsetKrw,
		@Schema(description = "원본 기준으로 확장을 켜면 현금으로 나가는 금액") long originalCashKrw,
		@Schema(description = "원본 기준 상계 대상 대리점 수") int originalOffsetAgencyCount,
		@Schema(description = "확장을 켜면 상계로 제안되는 금액 (현재 잔액 기준 계산 결과)") long proposedOffsetKrw,
		@Schema(description = "확장을 켜면 현금으로 나가는 금액") long proposedCashKrw,
		@Schema(description = "상계 대상 대리점 수") int offsetAgencyCount,
		@Schema(description = "회계·자금팀이 실제로 확정한 상계액") long settledOffsetKrw,
		@Schema(description = "상계 대상 대리점의 현재 기준 초과 연체 잔액") long overdueBeyondKrw,
		List<Line> lines) {

	@Schema(description = "장려금 한 건의 비교")
	public record Line(String rebateId, String agencyId, String agencyName, long amountKrw,
			@Schema(description = "원본 기준 상계액") long originalOffsetKrw,
			@Schema(description = "현재 잔액 기준 상계 제안액") long proposedOffsetKrw,
			@Schema(description = "대리점의 기준 초과 연체 잔액") long overdueBeyondKrw,
			@Schema(nullable = true, description = "지급 지시 상태") String paymentStatus,
			@Schema(description = "확정된 상계액 (결정 전이면 0)") long settledOffsetKrw,
			@Schema(nullable = true) String basis) {
	}
}
