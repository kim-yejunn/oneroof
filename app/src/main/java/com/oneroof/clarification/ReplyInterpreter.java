package com.oneroof.clarification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks an interpretation before anyone sees it: the stance must be one allowed for the request,
 * lists are bounded, and every amount and document must actually appear in the reply. Anything the
 * reply does not contain is dropped and the drop is noted, so the model cannot add facts.
 */
final class ReplyInterpreter {

	static final Map<ClarificationKind, Set<String>> STANCES = Map.of(ClarificationKind.RELATION_EXPLANATION,
			Set.of("EXPLAINS_RELATION", "DENIES_RELATION", "PARTIAL", "UNCLEAR"), ClarificationKind.ACCOUNT_CONFIRMATION,
			Set.of("CONFIRMS_ACCOUNT", "CHANGES_ACCOUNT", "UNCLEAR"), ClarificationKind.OFFSET_NOTICE,
			Set.of("ACCEPTS_OFFSET", "DISPUTES_OFFSET", "UNCLEAR"));

	static final Map<String, String> STANCE_LABELS = Map.of("EXPLAINS_RELATION", "관계 사유 설명", "DENIES_RELATION",
			"관계 부인", "PARTIAL", "일부만 설명", "UNCLEAR", "불분명", "CONFIRMS_ACCOUNT", "등록 계좌 확인", "CHANGES_ACCOUNT",
			"계좌 변경 요청", "ACCEPTS_OFFSET", "상계 수용", "DISPUTES_OFFSET", "상계 이의");

	private static final int MAX_ITEMS = 6;

	private static final int MAX_ITEM_LENGTH = 200;

	private ReplyInterpreter() {
	}

	/**
	 * @return the checked interpretation and notes on what was dropped
	 * @throws IllegalArgumentException when the interpretation is unusable as a whole
	 */
	static Checked check(ReplyInterpretation raw, ReplyInterpreterPort.Input input) {
		if (raw == null || raw.summary() == null || raw.summary().isBlank()) {
			throw new IllegalArgumentException("요약이 비어 있음");
		}
		if (raw.stance() == null || !STANCES.get(input.kind()).contains(raw.stance())) {
			throw new IllegalArgumentException("허용되지 않은 판단 구분: " + raw.stance());
		}
		List<String> notes = new ArrayList<>();
		String source = compact(input.replyText());
		List<Long> amounts = new ArrayList<>();
		for (Long amount : nullSafe(raw.amountsKrw())) {
			if (amount != null && amount > 0 && source.contains(String.valueOf(amount))) {
				amounts.add(amount);
			}
			else {
				notes.add("회신에 없는 금액 %s 제외".formatted(amount));
			}
		}
		List<String> evidence = new ArrayList<>();
		for (String item : bounded(raw.evidence())) {
			if (mentioned(item, source)) {
				evidence.add(item);
			}
			else {
				notes.add("회신에 없는 증빙 '%s' 제외".formatted(item));
			}
		}
		ReplyInterpretation checked = new ReplyInterpretation(truncate(raw.summary().trim(), 300), raw.stance(),
				bounded(raw.reasons()), evidence, amounts.stream().distinct().limit(MAX_ITEMS).toList(),
				bounded(raw.gaps()), bounded(raw.followUps()));
		return new Checked(checked, notes);
	}

	/** Whether some word of the item (two characters or more) appears in the reply. */
	private static boolean mentioned(String item, String source) {
		for (String word : item.split("[\\s·,()/]+")) {
			String cleaned = word.replaceAll("[^0-9A-Za-z가-힣]", "");
			if (cleaned.length() >= 2 && source.contains(cleaned)) {
				return true;
			}
		}
		return false;
	}

	/** The reply without spaces and thousands separators, so "1,200,000원" matches 1200000. */
	private static String compact(String text) {
		return text == null ? "" : text.replaceAll("[\\s,]", "");
	}

	private static List<String> bounded(List<String> items) {
		return nullSafe(items).stream()
			.filter((item) -> item != null && !item.isBlank())
			.map((item) -> truncate(item.trim(), MAX_ITEM_LENGTH))
			.distinct()
			.limit(MAX_ITEMS)
			.toList();
	}

	private static <T> List<T> nullSafe(List<T> items) {
		return items == null ? List.of() : items;
	}

	private static String truncate(String text, int length) {
		return text.length() <= length ? text : text.substring(0, length - 1) + "…";
	}

	record Checked(ReplyInterpretation interpretation, List<String> notes) {
	}
}
