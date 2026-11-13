package com.oneroof.clarification.adapter;

import java.net.http.HttpClient;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
 * <p>Free tiers are sometimes overloaded (503) or rate limited (429). {@code oneroof.ai.model} may
 * therefore list several models separated by commas; an overloaded model or a connection that fails
 * within seconds hands the request to the next one, within one time budget. A call that runs into
 * the read limit is not repeated: the rules take over instead.
 *
 * <p>Switched on with {@code oneroof.ai.provider=openai-compatible}.
 */
@Component
@Primary
@ConditionalOnProperty(name = "oneroof.ai.provider", havingValue = "openai-compatible")
public class OpenAiCompatibleReplyInterpreter implements ReplyInterpreterPort {

	private static final Duration BUDGET = Duration.ofSeconds(50);

	private static final Duration FAST_FAILURE = Duration.ofSeconds(10);

	private final RestClient http;
	private final List<String> models;
	private final ThreadLocal<String> answeredBy = new ThreadLocal<>();
	private final ObjectMapper json = JsonMapper.builder().build();

	@org.springframework.beans.factory.annotation.Autowired
	public OpenAiCompatibleReplyInterpreter(@Value("${oneroof.ai.base-url}") String baseUrl,
			@Value("${oneroof.ai.model}") String model, @Value("${oneroof.ai.api-key:}") String apiKey) {
		this(RestClient.builder()
			.requestFactory(requestFactory())
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey), model);
	}

	OpenAiCompatibleReplyInterpreter(RestClient.Builder http, String models) {
		this.http = http.build();
		this.models = java.util.Arrays.stream(models.split(",")).map(String::trim).filter((m) -> !m.isEmpty()).toList();
		if (this.models.isEmpty()) {
			throw new IllegalArgumentException("oneroof.ai.model 에 모델이 없음");
		}
	}

	/**
	 * A short connect limit so an unreachable endpoint fails at once, and a read limit long enough
	 * for the free tier, whose answers usually take a few seconds but sometimes over half a minute.
	 * HTTP/1.1 avoids HTTP/2 connection reuse that free endpoints sometimes drop.
	 */
	private static JdkClientHttpRequestFactory requestFactory() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10))
			.build());
		factory.setReadTimeout(Duration.ofSeconds(45));
		return factory;
	}

	@Override
	public String name() {
		return String.join(",", this.models);
	}

	@Override
	public String answeredBy() {
		String model = this.answeredBy.get();
		return model == null ? this.models.get(0) : model;
	}

	@Override
	public ReplyInterpretation interpret(Input input) {
		this.answeredBy.remove();
		long deadline = System.nanoTime() + BUDGET.toNanos();
		int attempts = Math.max(2, this.models.size() + 1);
		RuntimeException last = null;
		for (int attempt = 0; attempt < attempts; attempt++) {
			if (attempt > 0) {
				if (System.nanoTime() + Duration.ofSeconds(2).toNanos() > deadline) {
					break;
				}
				pause();
			}
			String model = this.models.get(attempt % this.models.size());
			long started = System.nanoTime();
			String body;
			try {
				body = send(request(model, input));
			}
			catch (HttpServerErrorException | HttpClientErrorException.TooManyRequests ex) {
				last = ex; // overloaded or rate limited: the next model may answer
				continue;
			}
			catch (ResourceAccessException ex) {
				if (Duration.ofNanos(System.nanoTime() - started).compareTo(FAST_FAILURE) > 0) {
					throw ex; // ran into the read limit: let the rules take over
				}
				last = ex;
				continue;
			}
			JsonNode choice = this.json.readTree(body).path("choices").path(0);
			String finish = choice.path("finish_reason").asString("");
			if (!"stop".equals(finish)) {
				// Cut off or blocked: not used; the caller falls back to the rules.
				throw new IllegalStateException("응답이 완결되지 않음: finish_reason=" + finish);
			}
			ReplyInterpretation result = parse(choice.path("message").path("content").asString(""));
			this.answeredBy.set(model);
			return result;
		}
		throw last != null ? last : new IllegalStateException("시간 안에 응답한 모델이 없음");
	}

	private static Map<String, Object> request(String model, Input input) {
		return Map.of("model", model, "response_format", Map.of("type", "json_object"), "messages",
				List.of(Map.of("role", "system", "content", ReplyPrompt.SYSTEM + "\n" + ReplyPrompt.JSON_SHAPE),
						Map.of("role", "user", "content", ReplyPrompt.user(input))));
	}

	private static void pause() {
		try {
			Thread.sleep(1500);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("중단됨", ex);
		}
	}

	private String send(Map<String, Object> request) {
		return this.http.post()
			.uri("/chat/completions")
			.contentType(MediaType.APPLICATION_JSON)
			.body(request)
			.retrieve()
			.body(String.class);
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
