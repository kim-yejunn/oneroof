package com.oneroof.console.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기준일의 관련자 판정 결과")
public record RelatedPartiesView(LocalDate asOf, List<GroupView> groups,
		@Schema(description = "그룹 밖에서 식별정보가 1개만 일치하는 쌍") List<LinkView> nearMisses,
		List<AgencyView> agencies) {

	public record AgencyView(String agencyId, String name, boolean rebateEligible, LocalDate registeredDate) {
	}
}
