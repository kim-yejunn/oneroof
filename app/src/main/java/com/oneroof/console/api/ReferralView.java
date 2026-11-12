package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "감사팀으로 넘긴 건. 부정을 확정한 것이 아니라 후속 확인 대상입니다.")
public record ReferralView(long id,
		@Schema(example = "RebateReview") String subjectType,
		String subjectId,
		String title,
		String reason,
		String referredBy,
		LocalDate referredOn,
		@Schema(allowableValues = { "OPEN" }) String status,
		String correlationId) {
}
