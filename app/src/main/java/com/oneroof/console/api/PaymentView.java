package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지급 지시와 은행 처리 상태")
public record PaymentView(long id,
		@Schema(example = "RebateClaim") String referenceType,
		@Schema(example = "RB-00221") String referenceId,
		String agencyId,
		String agencyName,
		long amountKrw,
		@Schema(allowableValues = { "REQUESTED", "HELD", "SENT", "PAID", "FAILED" }) String status,
		@Schema(nullable = true, description = "은행 전송 접수번호(모의)") String gatewayReference,
		@Schema(nullable = true, description = "은행 거래번호(모의)") String bankReference,
		LocalDate requestedOn,
		String correlationId,
		@Schema(nullable = true) String period,
		@Schema(nullable = true) Long reviewId,
		@Schema(nullable = true, description = "지급할 계좌 (마스터 등록 정산계좌 또는 대리점이 확인한 계좌)") String payeeAccount,
		@Schema(nullable = true, description = "보류 사유. 보류된 적 없으면 비어 있음") String holdReason,
		@Schema(nullable = true) Long clarificationId,
		@Schema(nullable = true) String clarificationStatus) {
}
