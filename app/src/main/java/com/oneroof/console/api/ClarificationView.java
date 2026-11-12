package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대리점에 보낸 소명·확인 요청과 회신. 발송과 회신은 모의 처리입니다.")
public record ClarificationView(long id,
		@Schema(allowableValues = { "RELATION_EXPLANATION", "ACCOUNT_CONFIRMATION" }) String kind,
		@Schema(description = "회신을 기다리는 대상", example = "RebateReview") String subjectType,
		String subjectId,
		@Schema(description = "쉼표로 구분한 대상 대리점", example = "D-011,D-024,D-037") String agencyIds,
		String title,
		@Schema(description = "대리점에 보낸 요청 전문") String question,
		@Schema(allowableValues = { "SENT", "ANSWERED", "EXPIRED" }) String status,
		LocalDate sentOn,
		@Schema(description = "회신 기한 (발송일 + 7일, 교육용 가정)") LocalDate dueOn,
		@Schema(nullable = true) String noticeReference,
		@Schema(nullable = true,
				allowableValues = { "EXPLAINED", "ACCOUNT_CONFIRMED", "ACCOUNT_CHANGED", "NO_REPLY" }) String outcome,
		@Schema(nullable = true) String replyText,
		@Schema(nullable = true) String replyAccount,
		@Schema(nullable = true) LocalDate repliedOn,
		String correlationId) {
}
