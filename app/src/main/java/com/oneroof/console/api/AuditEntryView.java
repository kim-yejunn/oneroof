package com.oneroof.console.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "감사 로그 한 줄. 추가만 되고 수정·삭제되지 않음")
public record AuditEntryView(long id,
		@Schema(description = "실제로 기록된 시각") OffsetDateTime recordedAt,
		@Schema(description = "시나리오 기준일", example = "2026-07-01") LocalDate businessDate,
		@Schema(example = "SALES_EXECUTIVE") String actorRole,
		@Schema(example = "APPROVAL_GRANTED") String action,
		@Schema(example = "ApprovalRequest") String subjectType,
		String subjectId,
		String detail,
		@Schema(allowableValues = { "ORIGINAL", "DEPARTMENT_CLAIM", "ASSUMPTION", "MOCK_REPLY", "CALCULATED" }) String provenance,
		@Schema(nullable = true) String correlationId) {
}
