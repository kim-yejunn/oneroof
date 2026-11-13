package com.oneroof.clarification;

import java.util.List;

/**
 * An agency's free-text reply put into a fixed shape for the person who decides next. It describes
 * what the reply says and what it leaves out; it does not judge the agency or decide anything.
 *
 * @param summary what the reply says, in one or two sentences
 * @param stance what the reply does, one of {@link ReplyInterpreter#STANCES} allowed for the request kind
 * @param reasons the reasons the agency gives
 * @param evidence the documents the agency says it attached or can provide
 * @param amountsKrw the amounts written in the reply, in won
 * @param gaps what the request asked that the reply does not answer
 * @param followUps what the reviewer should check before deciding
 */
public record ReplyInterpretation(String summary, String stance, List<String> reasons, List<String> evidence,
		List<Long> amountsKrw, List<String> gaps, List<String> followUps) {
}
