package com.oneroof.clarification.adapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oneroof.clarification.ClarificationKind;
import com.oneroof.clarification.ClarificationOutcome;
import com.oneroof.clarification.ReplyInterpretation;
import com.oneroof.clarification.ReplyInterpreterPort;
import org.springframework.stereotype.Component;

/**
 * Puts a reply into shape with fixed rules: documents by their usual Korean suffixes, amounts
 * written in won, and which shared identifiers the reply addresses. Used when no AI model is
 * configured and whenever the model fails, so the reviewer always sees something.
 */
@Component
public class RuleBasedReplyInterpreter implements ReplyInterpreterPort {

	private static final Pattern DOCUMENT = Pattern
		.compile("([가-힣A-Za-z]*\\s?(?:계약서|등기부|등본|확인서|사본|증명서|명세서|신고서|내역서|영수증|세금계산서))");

	private static final Pattern AMOUNT = Pattern.compile("([0-9][0-9,]{2,})\\s*원");

	private static final List<String[]> IDENTIFIERS = List.of(new String[] { "대표", "대표자를 함께 쓰는 사유" },
			new String[] { "주소", "주소를 함께 쓰는 사유" }, new String[] { "계좌", "계좌를 함께 쓰는 사유" });

	@Override
	public String name() {
		return "rule-based";
	}

	@Override
	public ReplyInterpretation interpret(Input input) {
		String reply = input.replyText() == null ? "" : input.replyText().trim();
		List<String> evidence = new ArrayList<>(documents(reply));
		List<Long> amounts = amounts(reply);
		List<String> gaps = new ArrayList<>();
		List<String> followUps = new ArrayList<>();
		evidence.forEach((document) -> followUps.add("제출 서류 원본 확인: " + document));
		if (input.kind() == ClarificationKind.RELATION_EXPLANATION) {
			for (String[] identifier : IDENTIFIERS) {
				if (input.question().contains(identifier[0]) && !reply.contains(identifier[0])) {
					gaps.add(identifier[1] + "에 대한 설명 없음");
				}
			}
		}
		if (input.outcome() == ClarificationOutcome.OFFSET_DISPUTED) {
			if (amounts.isEmpty()) {
				gaps.add("이의 금액이 적혀 있지 않음");
			}
			amounts.forEach((amount) -> followUps.add("반품·정산 내역과 금액 대조: %,d원".formatted(amount)));
		}
		if (evidence.isEmpty() && !reply.isEmpty()) {
			gaps.add("근거 서류 언급 없음");
		}
		return new ReplyInterpretation(summary(reply), stance(input), sentences(reply), evidence, amounts, gaps,
				followUps);
	}

	private static String stance(Input input) {
		return switch (input.outcome()) {
			case EXPLAINED -> "EXPLAINS_RELATION";
			case ACCOUNT_CONFIRMED -> "CONFIRMS_ACCOUNT";
			case ACCOUNT_CHANGED -> "CHANGES_ACCOUNT";
			case OFFSET_ACCEPTED -> "ACCEPTS_OFFSET";
			case OFFSET_DISPUTED -> "DISPUTES_OFFSET";
			case NO_REPLY -> "UNCLEAR";
		};
	}

	private static Set<String> documents(String reply) {
		Set<String> found = new LinkedHashSet<>();
		Matcher matcher = DOCUMENT.matcher(reply);
		while (matcher.find()) {
			found.add(matcher.group(1).trim());
		}
		return found;
	}

	private static List<Long> amounts(String reply) {
		List<Long> found = new ArrayList<>();
		Matcher matcher = AMOUNT.matcher(reply);
		while (matcher.find()) {
			found.add(Long.parseLong(matcher.group(1).replace(",", "")));
		}
		return found;
	}

	private static List<String> sentences(String reply) {
		return List.of(reply.split("(?<=[.。])\\s+|\\n")).stream().map(String::trim).filter((s) -> !s.isEmpty()).limit(3)
			.toList();
	}

	private static String summary(String reply) {
		if (reply.isEmpty()) {
			return "회신 내용 없음";
		}
		String first = reply.split("(?<=[.。])\\s+|\\n")[0].trim();
		return first.length() <= 120 ? first : first.substring(0, 119) + "…";
	}
}
