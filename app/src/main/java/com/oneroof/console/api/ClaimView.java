package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "판매장려금 한 건과 그 지급 상태")
public record ClaimView(
		@Schema(example = "RB-00221") String rebateId,
		@Schema(example = "D-011") String agencyId,
		String agencyName,
		String period,
		long amountKrw,
		long salesPerformanceKrw,
		@Schema(description = "원본 파일에 기록된 상태", example = "미지급") String originalStatus,
		@Schema(nullable = true) String originalApprover,
		@Schema(nullable = true) LocalDate originalPaymentDate,
		@Schema(allowableValues = { "SETTLED_IN_SOURCE", "REJECTED_IN_SOURCE", "PENDING", "UNDER_APPROVAL",
				"PAYMENT_REQUESTED", "PAID", "REJECTED" }) String settlementStatus,
		@Schema(nullable = true) Long reviewId,
		@Schema(description = "원본 파일과 줄 번호", example = "01_corevia/sales/sales_rebate_payment_cv.csv:L55") String sourceRef,
		@Schema(nullable = true) Long paymentInstructionId,
		@Schema(nullable = true, allowableValues = { "REQUESTED", "SENT", "PAID", "FAILED" }) String paymentStatus,
		@Schema(nullable = true) String gatewayReference,
		@Schema(nullable = true) String bankReference) {
}
