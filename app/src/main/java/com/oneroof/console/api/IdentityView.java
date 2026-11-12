package com.oneroof.console.api;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대리점 식별정보 한 줄. 정정되면 이전 값은 적용 종료일이 생깁니다.")
public record IdentityView(String agencyId,
		@Schema(allowableValues = { "REPRESENTATIVE", "ADDRESS", "BANK_ACCOUNT" }) String kind,
		String identityValue,
		LocalDate effectiveFrom,
		@Schema(nullable = true, description = "이 날부터 적용 안 됨. 비어 있으면 현재 값") LocalDate effectiveTo,
		String provenance,
		String sourceRef) {
}
