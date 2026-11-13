package com.oneroof.clarification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.oneroof.clarification.adapter.RuleBasedReplyInterpreter;
import org.junit.jupiter.api.Test;

/** What any interpreter, AI or rules, may put in front of a reviewer. No network. */
class ReplyInterpreterTests {

	private static final String QUESTION = "- D-011 ↔ D-024: 대표자, 주소 일치\n- D-011 ↔ D-037: 대표자, 계좌 일치\n공유 사유를 설명해 주십시오.";

	private static final String DISPUTE = "5월 반품 2건(1,200,000원) 정산이 미수금에 반영되지 않음. 반품 확인서 첨부";

	@Test
	void dropsAmountsAndDocumentsTheReplyDoesNotContain() {
		ReplyInterpreterPort.Input input = new ReplyInterpreterPort.Input(ClarificationKind.OFFSET_NOTICE,
				ClarificationOutcome.OFFSET_DISPUTED, "상계 통지", DISPUTE);
		ReplyInterpretation invented = new ReplyInterpretation("반품 정산 누락을 이유로 이의", "DISPUTES_OFFSET",
				List.of("반품 미반영"), List.of("반품 확인서", "세금계산서 사본"), List.of(1_200_000L, 5_000_000L),
				List.of(), List.of("반품 내역 대조"));

		ReplyInterpreter.Checked checked = ReplyInterpreter.check(invented, input);

		assertThat(checked.interpretation().amountsKrw()).containsExactly(1_200_000L);
		assertThat(checked.interpretation().evidence()).containsExactly("반품 확인서");
		assertThat(checked.notes()).anyMatch((note) -> note.contains("5000000"))
			.anyMatch((note) -> note.contains("세금계산서 사본"));
	}

	@Test
	void rejectsAStanceThatDoesNotFitTheRequest() {
		ReplyInterpreterPort.Input input = new ReplyInterpreterPort.Input(ClarificationKind.OFFSET_NOTICE,
				ClarificationOutcome.OFFSET_DISPUTED, "상계 통지", DISPUTE);
		ReplyInterpretation wrong = new ReplyInterpretation("요약", "EXPLAINS_RELATION", List.of(), List.of(), List.of(),
				List.of(), List.of());

		assertThatThrownBy(() -> ReplyInterpreter.check(wrong, input)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rulesFindDocumentsAmountsAndUnansweredIdentifiers() {
		RuleBasedReplyInterpreter rules = new RuleBasedReplyInterpreter();

		ReplyInterpretation relation = rules.interpret(new ReplyInterpreterPort.Input(
				ClarificationKind.RELATION_EXPLANATION, ClarificationOutcome.EXPLAINED, QUESTION,
				"세 법인은 대표가 같은 별도 법인으로 성남 사무실을 함께 임차함. 임대차계약서·법인등기부 첨부"));
		assertThat(relation.evidence()).containsExactly("임대차계약서", "법인등기부");
		assertThat(relation.gaps()).anyMatch((gap) -> gap.startsWith("계좌"));

		ReplyInterpretation dispute = rules.interpret(new ReplyInterpreterPort.Input(ClarificationKind.OFFSET_NOTICE,
				ClarificationOutcome.OFFSET_DISPUTED, "상계 통지", DISPUTE));
		assertThat(dispute.stance()).isEqualTo("DISPUTES_OFFSET");
		assertThat(dispute.amountsKrw()).containsExactly(1_200_000L);
		assertThat(ReplyInterpreter.check(dispute, new ReplyInterpreterPort.Input(ClarificationKind.OFFSET_NOTICE,
				ClarificationOutcome.OFFSET_DISPUTED, "상계 통지", DISPUTE)).notes()).isEmpty();
	}
}
