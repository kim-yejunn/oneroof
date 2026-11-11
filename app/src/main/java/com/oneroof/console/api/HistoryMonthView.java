package com.oneroof.console.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관련자 그룹의 한 달 합산")
public record HistoryMonthView(String period, long totalKrw, long limitKrw, boolean exceedsLimit,
		String requiredApprover,
		@Schema(description = "원본 기록상 그 달을 승인한 주체") List<String> originalApprovers,
		List<String> statuses) {
}
