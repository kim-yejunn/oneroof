package com.oneroof.console.api;

import com.oneroof.console.api.Commands.BankRepliesResult;
import com.oneroof.console.api.Commands.BankReplyRequest;
import com.oneroof.console.api.Commands.BulkDecisionRequest;
import com.oneroof.console.api.Commands.BulkDecisionResult;
import com.oneroof.console.api.Commands.ClarificationReplyRequest;
import com.oneroof.console.api.Commands.MasterChangeRequest;
import com.oneroof.console.api.Commands.DecisionRequest;
import com.oneroof.console.api.Commands.DecisionResult;
import com.oneroof.console.api.Commands.ReceiptView;
import com.oneroof.console.api.Commands.SettlementRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Everything the console can ask the system to do. The acting role is sent in a header, which
 * simulates signing in as that role; whether the role has the authority is decided by the module
 * that owns the subject.
 */
@Tag(name = "처리", description = "정산 접수, 승인·반려, 마스터 정정, 모의 은행·대리점 회신. 결과는 모듈 사이 이벤트로 이어지며 커밋 직후 비동기로 반영됩니다.")
@RequestMapping("/api")
public interface ConsoleCommandApi {

	String ROLE_HEADER = "X-Oneroof-Role";

	@Operation(summary = "정산 접수",
			description = "한 달치 미지급 장려금을 한도 단위로 묶어 권한자에게 상신합니다. 같은 달을 다시 보내면 아무것도 하지 않습니다.")
	@ApiResponse(responseCode = "200", description = "접수 결과")
	@ApiResponse(responseCode = "400", description = "잘못된 요청",
			content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@PostMapping("/settlements")
	ReceiptView submitSettlement(@RoleHeader @RequestHeader(ROLE_HEADER) String role,
			@Valid @RequestBody SettlementRequest request);

	@Operation(summary = "승인 또는 반려", description = "승인 요청에 적힌 권한 역할만 결정할 수 있습니다.")
	@ApiResponse(responseCode = "200", description = "결정 결과")
	@ApiResponse(responseCode = "403", description = "권한 없는 역할",
			content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "이미 결정된 요청",
			content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@PostMapping("/approvals/{id}/decision")
	DecisionResult decide(@RoleHeader @RequestHeader(ROLE_HEADER) String role,
			@Parameter(description = "승인 요청 ID", example = "38") @PathVariable long id,
			@RequestBody DecisionRequest request);

	@Operation(summary = "일괄 결정", description = "여러 승인 요청을 한 번에 결정합니다. 하나라도 권한이 없으면 전부 취소됩니다.")
	@ApiResponse(responseCode = "200", description = "결정 건수")
	@ApiResponse(responseCode = "403", description = "권한 없는 요청 포함",
			content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@PostMapping("/approvals/decisions")
	BulkDecisionResult decideAll(@RoleHeader @RequestHeader(ROLE_HEADER) String role,
			@Valid @RequestBody BulkDecisionRequest request);

	@Operation(summary = "모의 은행 회신 (1건)", description = "은행이 지급 결과를 알려 온 것을 흉내 냅니다. 실제 이체는 일어나지 않습니다.")
	@ApiResponse(responseCode = "200", description = "접수 결과")
	@PostMapping("/payments/{id}/bank-reply")
	ReceiptView bankReply(@Parameter(description = "지급 지시 ID") @PathVariable long id,
			@RequestBody BankReplyRequest request);

	@Operation(summary = "모의 은행 회신 (대기 전체)", description = "은행 회신을 기다리는 모든 지급 지시에 이체 완료를 회신합니다.")
	@PostMapping("/payments/bank-replies")
	BankRepliesResult bankReplies();

	@Operation(summary = "마스터 정정 신고",
			description = "대리점의 대표자·주소·정산계좌 정정을 접수합니다. 관련자 그룹이 다시 계산되어 결정 전 검토는 재분류되고, "
					+ "전결로 처리된 지난달이 임원 승인 대상이었다면 사후 확인 요청이 생깁니다.")
	@ApiResponse(responseCode = "200", description = "접수 결과")
	@ApiResponse(responseCode = "400", description = "반영할 수 없는 정정",
			content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@PostMapping("/master-changes")
	ReceiptView reportMasterChange(@RoleHeader @RequestHeader(ROLE_HEADER) String role,
			@Valid @RequestBody MasterChangeRequest request);

	@Operation(summary = "모의 대리점 회신",
			description = "소명·계좌 확인 요청에 대리점이 회신한 것을 흉내 냅니다. 요청 하나에 회신 하나만 반영됩니다.")
	@ApiResponse(responseCode = "200", description = "접수 결과")
	@ApiResponse(responseCode = "400", description = "요청 종류에 맞지 않는 회신",
			content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	@PostMapping("/clarifications/{id}/reply")
	ReceiptView replyToClarification(@Parameter(description = "소명·확인 요청 ID") @PathVariable long id,
			@Valid @RequestBody ClarificationReplyRequest request);

	/** Documents the role header once for every command that takes it. */
	@Parameter(name = ROLE_HEADER, in = ParameterIn.HEADER, required = true,
			description = "행동하는 역할 (시연용 역할 전환)",
			schema = @Schema(allowableValues = { "SALES_SUPPORT", "SALES_EXECUTIVE" }, example = "SALES_EXECUTIVE"))
	@java.lang.annotation.Target(java.lang.annotation.ElementType.PARAMETER)
	@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@interface RoleHeader {
	}
}
