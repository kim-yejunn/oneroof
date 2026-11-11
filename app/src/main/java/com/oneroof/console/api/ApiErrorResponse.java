package com.oneroof.console.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "요청이 거부됐을 때의 응답")
public record ApiErrorResponse(
		@Schema(description = "AUTHORITY: 권한 없음, STATE: 현재 상태에서 할 수 없는 처리, REQUEST: 잘못된 요청, NOT_FOUND: 대상 없음",
				example = "AUTHORITY") String code,
		@Schema(description = "화면에 그대로 보여줄 수 있는 설명", example = "영업임원 권한 사안이라 영업지원팀 역할은 결정할 수 없음 (승인 요청 #38)") String message) {
}
