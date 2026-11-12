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
		@Schema(allowableValues = { "OFFSET_REVIEW", "OFFSET_SETTLED", "OFFSET_DISPUTED", "REQUESTED", "HELD", "SENT", "PAID", "FAILED" }) String status,
		@Schema(nullable = true, description = "은행 전송 접수번호(모의)") String gatewayReference,
		@Schema(nullable = true, description = "은행 거래번호(모의)") String bankReference,
		LocalDate requestedOn,
		String correlationId,
		@Schema(nullable = true) String period,
		@Schema(nullable = true) Long reviewId,
		@Schema(nullable = true, description = "지급할 계좌 (마스터 등록 정산계좌 또는 대리점이 확인한 계좌)") String payeeAccount,
		@Schema(nullable = true, description = "보류 사유. 보류된 적 없으면 비어 있음") String holdReason,
		@Schema(nullable = true) Long clarificationId,
		@Schema(nullable = true) String clarificationStatus,
		@Schema(description = "연체 미수금과 상계한 금액 (결정 전이면 제안액)") long offsetKrw,
		@Schema(description = "은행으로 보낼 금액 = 지급액 − 상계액") long netKrw,
		@Schema(nullable = true, description = "상계 근거가 된 연체 송장") String offsetBasis,
		@Schema(description = "0 상계 없음, 1 회계·자금 1차 결정, 2 대리점 이의 뒤 재검토") int offsetRound,
		@Schema(nullable = true, description = "회계·자금팀 결정 내용") String offsetDecision) {
}
