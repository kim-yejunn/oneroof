package com.oneroof.console.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "두 대리점 사이의 일치. 2개 이상이면 동일 관련자")
public record LinkView(String agencyA, String agencyB,
		@Schema(description = "식별정보 2개 이상 일치") boolean related,
		@Schema(description = "마스터 등록 값만 비교해도 2개 이상 일치하는지") boolean relatedInMasterOnly,
		List<MatchView> matches) {
}
