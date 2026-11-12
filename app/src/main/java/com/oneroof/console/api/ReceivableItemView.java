package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "미수금 송장 한 건과 이후 변동")
public record ReceivableItemView(String arId,
		String agencyId,
		String agencyName,
		LocalDate invoiceDate,
		LocalDate dueDate,
		long amountKrw,
		@Schema(description = "원본 미회수 잔액") long originalOutstandingKrw,
		long offsetKrw,
		long depositKrw,
		@Schema(description = "지금 남은 잔액") long outstandingKrw,
		@Schema(description = "연체일수 (원본, 2026-06-30 기준)") int agingDays,
		@Schema(example = "01_corevia/financials/fin_ar_aging_cv.csv#L74") String sourceRef) {
}
