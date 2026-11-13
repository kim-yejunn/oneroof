package com.oneroof.clarification.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.oneroof.clarification.ClarificationKind;
import com.oneroof.clarification.ClarificationOutcome;
import com.oneroof.clarification.ReplyInterpretation;
import com.oneroof.clarification.ReplyInterpreterPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The OpenAI-compatible adapter against a canned provider response. No network. */
class OpenAiCompatibleReplyInterpreterTests {

	private static final ReplyInterpreterPort.Input INPUT = new ReplyInterpreterPort.Input(
			ClarificationKind.OFFSET_NOTICE, ClarificationOutcome.OFFSET_DISPUTED, "상계 통지",
			"5월 반품 2건(1,200,000원) 정산이 미수금에 반영되지 않음. 반품 확인서 첨부");

	@Test
	void sendsTheReplyAndReadsTheJsonAnswer() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://provider.test/v1/chat/completions"))
			.andExpect(header("Content-Type", "application/json"))
			.andExpect(jsonPath("$.model").value("free-model"))
			.andExpect(jsonPath("$.response_format.type").value("json_object"))
			.andRespond(withSuccess("""
					{"choices":[{"finish_reason":"stop","message":{"content":
					"```json\\n{\\"summary\\":\\"반품 미반영을 이유로 이의\\",\\"stance\\":\\"DISPUTES_OFFSET\\",\\"reasons\\":[\\"반품 2건 미차감\\"],\\"evidence\\":[\\"반품 확인서\\"],\\"amountsKrw\\":[\\"1,200,000\\"],\\"gaps\\":[],\\"followUps\\":[\\"반품 내역 대조\\"]}\\n```"}}]}
					""", MediaType.APPLICATION_JSON));
		OpenAiCompatibleReplyInterpreter interpreter = new OpenAiCompatibleReplyInterpreter(builder, "free-model");

		ReplyInterpretation result = interpreter.interpret(INPUT);

		assertThat(result.stance()).isEqualTo("DISPUTES_OFFSET");
		assertThat(result.amountsKrw()).containsExactly(1_200_000L);
		assertThat(result.evidence()).containsExactly("반품 확인서");
		server.verify();
	}

	@Test
	void refusesAnAnswerThatWasCutOff() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://provider.test/v1/chat/completions"))
			.andRespond(withSuccess("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{\\\"sum\"}}]}",
					MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> new OpenAiCompatibleReplyInterpreter(builder, "free-model").interpret(INPUT))
			.isInstanceOf(IllegalStateException.class);
	}
}
