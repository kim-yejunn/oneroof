package com.oneroof.clarification.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oneroof.clarification.ReplyInterpretation;
import com.oneroof.clarification.ReplyInterpreterPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Interprets replies through any provider that speaks the OpenAI chat completions format, which
 * covers free tiers such as Google Gemini (AI Studio key), Groq and OpenRouter. Only the base URL,
 * model and key change between them. The model is asked for a JSON object; whatever comes back is
 * parsed leniently here and checked strictly by {@code ReplyInterpreter} afterwards.
 *
 * <p>Switched on with {@code oneroof.ai.provider=openai-compatible}.
 */
@Component
@Primary
@ConditionalOnProperty(name = "oneroof.ai.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleReplyInterpreter implements ReplyInterpreterPort {

	private final RestClient http;
	private final String model;
	private final ObjectMapper json = JsonMapper.builder().build();

	@org.springframework.beans.factory.annotation.Autowired
	public OpenAiCompatibleReplyInterpreter(@Value("${oneroof.ai.base-url}") String baseUrl,
			@Value("${oneroof.ai.model}") String model, @Value("${oneroof.ai.api-key:}") String apiKey) {
		this(RestClient.builder()
			.requestFactory(requestFactory())
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey), model);
	}

	OpenAiCompatibleReplyInterpreter(RestClient.Builder http, String model) {
		this.http = http.build();
		this.model = model;
	}

	private static JdkClientHttpRequestFactory requestFactory() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
		factory.setReadTimeout(Duration.ofSeconds(60));
		return factory;
	}

	@Override
	public String name() {
		return this.model;
	}

	@Override
	public ReplyInterpretation interpret(Input input) {
		Map<String, Object> request = Map.of("model", this.model, "response_format", Map.of("type", "json_object"),
				"messages", List.of(Map.of("role", "system", "content", ReplyPrompt.SYSTEM + "\n" + ReplyPrompt.JSON_SHAPE),
						Map.of("role", "user", "content", ReplyPrompt.user(input))));
		String body = this.http.post()
			.uri("/chat/completions")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(String.class);
		JsonNode choice = this.json.readTree(body).path("choices").path(0);
		String finish = choice.path("finish_reason").asString("");
		if (!"stop".equals(finish)) {
			// Cut off or blocked: not used; the caller falls back to the rules.
			throw new IllegalStateException("응답이 완결되지 않음: finish_reason=" + finish);
		}
		return parse(choice.path("message").path("content").asString(""));
	}

	/** Reads the model's JSON, tolerating a code fence around it. Unknown keys are ignored. */
	ReplyInterpretation parse(String content) {
		String trimmed = content.strip();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
		}
		JsonNode node = this.json.readTree(trimmed);
		List<Long> amounts = new ArrayList<>();
		for (JsonNode amount : node.path("amountsKrw")) {
			if (amount.isNumber()) {
				amounts.add(amount.asLong());
			}
			else if (amount.isString() && amount.asString().replaceAll("[^0-9]", "").length() > 0) {
				amounts.add(Long.parseLong(amount.asString().replaceAll("[^0-9]", "")));
			}
		}
		return new ReplyInterpretation(node.path("summary").asString(""), node.path("stance").asString(""),
				strings(node.path("reasons")), strings(node.path("evidence")), amounts, strings(node.path("gaps")),
				strings(node.path("followUps")));
	}

	private static List<String> strings(JsonNode array) {
		List<String> values = new ArrayList<>();
		for (JsonNode item : array) {
			if (item.isString()) {
				values.add(item.asString());
			}
		}
		return values;
	}
}
