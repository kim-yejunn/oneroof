package com.oneroof.console.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "하나의 관련자로 보는 대리점 묶음과 그 근거")
public record GroupView(@Schema(example = "RPG-D-011") String groupKey, List<Member> members, List<LinkView> links,
		@Schema(nullable = true) List<HistoryMonthView> history) {

	public record Member(String agencyId, String name, String businessRegNo, LocalDate registeredDate, String sourceRef) {
	}
}
