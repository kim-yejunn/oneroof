package com.oneroof.console.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검토 하나를 결정하는 데 필요한 모든 것: 금액, 결정, 근거, 처리 이력")
public record ReviewDetailView(long id, String period, String unitKey, boolean relatedGroup, String memberAgencyIds,
		long totalKrw, long limitKrw, boolean exceedsLimit,
		@Schema(allowableValues = { "SALES_SUPPORT", "SALES_EXECUTIVE" }) String requiredApprover,
		@Schema(allowableValues = { "AWAITING_APPROVAL", "APPROVED", "REJECTED", "PAID" }) String status, String title,
		@Schema(description = "승인자가 읽는 근거 설명. 모든 숫자는 계산 결과") String rationale, String correlationId,
		@Schema(nullable = true) Long approvalRequestId, @Schema(nullable = true) String approvalStatus,
		@Schema(nullable = true) String decidedByRole, @Schema(nullable = true) String decidedByName,
		@Schema(nullable = true) LocalDate decidedOn, @Schema(nullable = true) String decisionComment, long claimCount,
		long paidCount, List<ClaimView> claims, List<AuditEntryView> audit,
		@Schema(description = "관련자 그룹일 때만: 구성원과 일치한 식별정보", nullable = true) GroupView evidence,
		@Schema(description = "관련자 그룹일 때만: 기록된 모든 달의 그룹 합산", nullable = true) List<HistoryMonthView> history) {
}
