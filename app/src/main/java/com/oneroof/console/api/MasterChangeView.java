package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "마스터 정정 이력. 값을 덮어쓰지 않고 적용일로 이어 붙입니다.")
public record MasterChangeView(long id,
		@Schema(description = "정정을 접수한 사건 ID") String eventId,
		String agencyId,
		@Schema(allowableValues = { "REPRESENTATIVE", "ADDRESS", "BANK_ACCOUNT" }) String kind,
		String previousValue,
		String newValue,
		LocalDate effectiveFrom,
		String reason,
		String reportedBy,
		@Schema(allowableValues = { "DEPARTMENT_CLAIM", "MOCK_REPLY" }) String provenance,
		LocalDate recordedOn) {
}
