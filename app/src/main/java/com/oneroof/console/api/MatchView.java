package com.oneroof.console.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "두 대리점이 공유하는 식별정보 하나")
public record MatchView(@Schema(allowableValues = { "REPRESENTATIVE", "ADDRESS", "BANK_ACCOUNT" }) String kind,
		@Schema(example = "정산계좌") String kindLabel, EvidenceView left, EvidenceView right) {
}
