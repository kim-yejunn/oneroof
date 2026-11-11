package com.oneroof.console.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "한 대리점의 식별정보 하나와 그 출처")
public record EvidenceView(String agencyId,
		@Schema(example = "국민 412-27-556310") String value,
		@Schema(allowableValues = { "MASTER_REGISTRATION", "OBSERVED_DEPOSIT" }) String basis,
		@Schema(example = "실제 입금 계좌") String basisLabel,
		String provenance,
		@Schema(example = "01_corevia/financials/fin_collection_history_cv.csv:L14") String sourceRef) {
}
