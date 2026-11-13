package com.oneroof.console.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

/** Request and response bodies of the commands. */
public final class Commands {

	private Commands() {
	}

	@Schema(description = "정산 접수")
	public record SettlementRequest(
			@NotNull @Pattern(regexp = "\\d{4}-\\d{2}") @Schema(description = "정산할 달", example = "2026-06") String period) {
	}

	@Schema(description = "승인 또는 반려")
	public record DecisionRequest(@Schema(description = "true 승인, false 반려") boolean approved,
			@Schema(description = "결정 사유", nullable = true, example = "합산 금액 확인 후 승인") String comment) {
	}

	@Schema(description = "여러 승인 요청을 한 번에 결정")
	public record BulkDecisionRequest(@NotEmpty List<Long> ids, boolean approved,
			@Schema(nullable = true, example = "한도 이내 전결") String comment) {
	}

	@Schema(description = "모의 은행 회신")
	public record BankReplyRequest(@Schema(description = "true 이체 완료, false 이체 실패") boolean succeeded) {
	}

	@Schema(description = "시연 초기화 결과")
	public record ResetResult(@Schema(description = "비운 테이블 수") int tablesCleared) {
	}

	@Schema(description = "대리점 입금 모의 통지. 가장 오래 연체된 미수금부터 줄어듭니다.")
	public record DepositRequest(
			@Positive @Schema(description = "입금액", example = "10500000") long amountKrw,
			@NotNull @Schema(description = "입금 계좌", example = "신한 778-02-213907") String bankAccount) {
	}

	@Schema(description = "대리점 마스터 정정 신고")
	public record MasterChangeRequest(
			@NotNull @Schema(example = "D-041") String agencyId,
			@NotNull @Schema(allowableValues = { "REPRESENTATIVE", "ADDRESS", "BANK_ACCOUNT" }, example = "ADDRESS") String kind,
			@NotNull @Schema(description = "새로 등록할 값", example = "대전시 중앙로 260") String newValue,
			@NotNull @Schema(description = "새 값이 적용되는 날 (기준일 이전)", example = "2026-01-01") java.time.LocalDate effectiveFrom,
			@NotNull @Schema(description = "정정 사유", example = "2025-12 사업장 이전 신고서 접수, 마스터 미반영 확인") String reason) {
	}

	@Schema(description = "대리점의 모의 회신. NO_REPLY 는 회신 기한이 지난 것을 흉내 냅니다.")
	public record ClarificationReplyRequest(
			@NotNull @Schema(allowableValues = { "EXPLAINED", "ACCOUNT_CONFIRMED", "ACCOUNT_CHANGED", "OFFSET_ACCEPTED", "OFFSET_DISPUTED", "NO_REPLY" }) String outcome,
			@Schema(nullable = true, description = "대리점이 쓴 회신 내용",
					example = "D-011·D-024 는 같은 대표의 별도 법인으로 사무실을 함께 임차함. 임대차계약서 첨부") String replyText,
			@Schema(nullable = true, description = "ACCOUNT_CHANGED 일 때 새 지급 계좌", example = "국민 412-27-556310") String replyAccount) {
	}

	@Schema(description = "사건 접수 결과")
	public record ReceiptView(@Schema(example = "EVT-SETTLEMENT-2026-06") String eventId,
			@Schema(description = "false면 같은 사건이 이미 접수되어 아무것도 하지 않음") boolean accepted) {
	}

	public record DecisionResult(long id, @Schema(allowableValues = { "APPROVED", "REJECTED" }) String status) {
	}

	public record BulkDecisionResult(@Schema(description = "결정한 건수") int decided) {
	}

	public record BankRepliesResult(@Schema(description = "회신을 입력한 지급 지시 건수") int replied) {
	}
}
