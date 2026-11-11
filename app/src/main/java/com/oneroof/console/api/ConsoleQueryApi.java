package com.oneroof.console.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Everything the console reads. Read-only; nothing here changes state. */
@Tag(name = "조회", description = "정산 현황, 검토, 관련자 근거, 지급, 감사 로그 조회. 상태를 바꾸지 않습니다.")
@RequestMapping("/api")
public interface ConsoleQueryApi {

	@Operation(summary = "기준 정보", description = "기준일, 전환 가능한 역할, 출처 구분, 적용 중인 월 한도")
	@GetMapping("/session")
	SessionView session();

	@Operation(summary = "월별 정산 현황", description = "달마다 장려금 건수·합계와 정산 단계별 건수. 최근 달부터")
	@GetMapping("/periods")
	List<PeriodView> periods();

	@Operation(summary = "검토 목록", description = "한도 단위(관련자 그룹 또는 단독 대리점)별 검토. 한도 초과 건이 먼저 나옵니다.")
	@GetMapping("/reviews")
	List<ReviewView> reviews(
			@Parameter(description = "이 달만 조회", example = "2026-06") @RequestParam(required = false) String period);

	@Operation(summary = "검토 상세", description = "결정에 필요한 금액, 장려금 내역, 관련자 근거, 월별 합산, 처리 이력")
	@ApiResponse(responseCode = "200", description = "검토 상세")
	@ApiResponse(responseCode = "404", description = "검토 없음",
			content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@GetMapping("/reviews/{id}")
	ReviewDetailView review(@Parameter(description = "검토 ID", example = "38") @PathVariable long id);

	@Operation(summary = "역할별 승인 대기", description = "그 역할이 결정해야 할 승인 요청. 금액이 큰 순서")
	@GetMapping("/approvals/pending")
	List<PendingApprovalView> pendingApprovals(@Parameter(description = "역할",
			schema = @Schema(allowableValues = { "SALES_SUPPORT", "SALES_EXECUTIVE" })) @RequestParam String role);

	@Operation(summary = "지급 지시 목록")
	@GetMapping("/payments")
	List<PaymentView> payments(@Parameter(description = "이 상태만 조회",
			schema = @Schema(allowableValues = { "REQUESTED", "SENT", "PAID", "FAILED" })) @RequestParam(required = false) String status);

	@Operation(summary = "감사 로그", description = "최근 기록부터. 사건 ID로 한 정산에서 생긴 기록만 볼 수 있습니다.")
	@GetMapping("/audit")
	List<AuditEntryView> audit(
			@Parameter(description = "사건 ID", example = "EVT-SETTLEMENT-2026-06") @RequestParam(required = false) String correlationId,
			@Parameter(description = "최대 건수 (1~1000)") @RequestParam(defaultValue = "300") int limit);

	@Operation(summary = "관련자 판정",
			description = "기준일에 동일 관련자로 보는 그룹과 근거, 그룹 밖 근접 쌍. 마스터 등록 값과 실제 입금 계좌를 함께 대조합니다.")
	@GetMapping("/related-parties")
	RelatedPartiesView relatedParties();
}
