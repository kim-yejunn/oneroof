package com.oneroof.console.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "화면이 시작할 때 필요한 기준 정보")
public record SessionView(
		@Schema(description = "시나리오의 \"오늘\". 모든 날짜 계산의 기준", example = "2026-07-01") LocalDate businessDate,
		@Schema(description = "시연에서 전환할 수 있는 역할") List<CodeLabel> roles,
		@Schema(description = "값의 출처 구분") List<CodeLabel> provenances,
		@Schema(description = "기준일에 적용되는 판매장려금 월 한도") LimitPolicyView limitPolicy) {

	public record CodeLabel(@Schema(example = "SALES_EXECUTIVE") String code, @Schema(example = "영업임원") String label) {
	}

	public record LimitPolicyView(@Schema(example = "2000000") long monthlyLimitKrw,
			@Schema(example = "2024-01-01") LocalDate effectiveFrom,
			@Schema(description = "ORIGINAL: 원본 정책 문서", example = "ORIGINAL") String provenance,
			@Schema(example = "00_common/approval_authority_policy.md#판매장려금 지급 한도") String sourceRef) {
	}
}
