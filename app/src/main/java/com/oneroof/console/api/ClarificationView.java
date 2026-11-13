package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대리점에 보낸 소명·확인 요청과 회신. 발송과 회신은 모의 처리입니다.")
public record ClarificationView(long id,
		@Schema(allowableValues = { "RELATION_EXPLANATION", "ACCOUNT_CONFIRMATION", "OFFSET_NOTICE" }) String kind,
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
				allowableValues = { "EXPLAINED", "ACCOUNT_CONFIRMED", "ACCOUNT_CHANGED", "OFFSET_ACCEPTED", "OFFSET_DISPUTED", "NO_REPLY" }) String outcome,
		@Schema(nullable = true) String replyText,
		@Schema(nullable = true) String replyAccount,
		@Schema(nullable = true) LocalDate repliedOn,
		String correlationId,
		@Schema(nullable = true, description = "회신을 해석한 주체: 모델 ID 또는 rule-based") String aiInterpreter,
		@Schema(nullable = true, description = "AI 해석이 실패해 규칙 기반으로 대체했는지") Boolean aiFellBack,
		@Schema(nullable = true, description = "회신 요지 (AI 생성, 검토용)") String aiSummary,
		@Schema(nullable = true, description = "회신이 하는 일", allowableValues = { "EXPLAINS_RELATION", "DENIES_RELATION",
				"PARTIAL", "UNCLEAR", "CONFIRMS_ACCOUNT", "CHANGES_ACCOUNT", "ACCEPTS_OFFSET", "DISPUTES_OFFSET" }) String aiStance,
		@Schema(nullable = true, description = "대리점이 든 사유, 줄바꿈 구분") String aiReasons,
		@Schema(nullable = true, description = "회신에 언급된 증빙 서류, 줄바꿈 구분 (회신에 없는 항목은 제외됨)") String aiEvidence,
		@Schema(nullable = true, description = "회신에 적힌 금액(원), 줄바꿈 구분 (회신에 없는 금액은 제외됨)") String aiAmounts,
		@Schema(nullable = true, description = "요청이 물었지만 회신이 답하지 않은 부분, 줄바꿈 구분") String aiGaps,
		@Schema(nullable = true, description = "결정 전에 확인할 일, 줄바꿈 구분") String aiFollowUps,
		@Schema(nullable = true, description = "검증에서 제외한 항목·대체 사유, 줄바꿈 구분") String aiNotes) {
}
