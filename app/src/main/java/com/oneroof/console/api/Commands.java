package com.oneroof.console.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
