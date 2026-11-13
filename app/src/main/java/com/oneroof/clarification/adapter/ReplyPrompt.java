package com.oneroof.clarification.adapter;

import com.oneroof.clarification.ReplyInterpreterPort;
import com.oneroof.clarification.ReplyInterpreterStances;

/** The instructions every model adapter gives, so the providers differ only in transport. */
final class ReplyPrompt {

	static final String SYSTEM = """
			당신은 판매장려금 지급 통제 시스템에서 대리점 회신을 정리하는 보조자입니다.
			회사가 대리점에 보낸 요청과 대리점의 회신을 받아, 다음 결정권자가 빨리 읽을 수 있게 구조화합니다.

			지켜야 할 것:
			- 회신에 적힌 내용만 옮깁니다. 회신에 없는 사실·서류·금액을 만들지 않습니다.
			- 부정 여부나 승인·반려를 판단하지 않습니다. 계산하지 않습니다.
			- amountsKrw 에는 회신에 숫자로 적힌 원화 금액만 정수로 넣습니다.
			- evidence 에는 대리점이 첨부했거나 제출하겠다고 한 서류 이름을 회신의 표현 그대로 넣습니다.
			- gaps 에는 요청이 물었는데 회신이 답하지 않은 부분을 적습니다. 예: 관계 소명 요청이 주소·계좌 공유를 물었는데 계좌 이야기가 없으면 그 점.
			- followUps 에는 결정권자가 결정 전에 확인할 일을 짧게 적습니다.
			- 모든 문장은 한국어, 짧게 씁니다.
			""";

	/** The JSON shape, spelled out for providers that only guarantee "some JSON object". */
	static final String JSON_SHAPE = """
			다음 키만 가진 JSON 객체 하나로만 답합니다. 다른 글은 쓰지 않습니다.
			{"summary": 문자열(회신 요지 한두 문장), "stance": 문자열(허용 값 중 하나),
			 "reasons": [문자열], "evidence": [문자열], "amountsKrw": [정수],
			 "gaps": [문자열], "followUps": [문자열]}
			""";

	private ReplyPrompt() {
	}

	static String user(ReplyInterpreterPort.Input input) {
		return """
				요청 종류: %s
				대리점 회신 구분: %s
				허용되는 stance 값: %s

				[회사가 보낸 요청]
				%s

				[대리점 회신]
				%s
				""".formatted(input.kind().label(), input.outcome().label(),
				String.join(", ", ReplyInterpreterStances.allowed(input.kind())), input.question(), input.replyText());
	}
}
