package com.oneroof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

/** The console's HTTP surface over the original data: what it shows and what it lets each role do. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "oneroof.offset.enabled=false")
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsoleApiTests {

	private final HttpClient http = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	private int port;

	@Test
	void servesTheSettlementConsoleEndToEnd() throws Exception {
		assertThat(get("/healthz").body()).isEqualTo("ok");

		String session = get("/api/session").body();
		assertThat(JsonPath.<String>read(session, "$.businessDate")).isEqualTo("2026-07-01");
		assertThat(JsonPath.<Number>read(session, "$.limitPolicy.monthlyLimitKrw").longValue()).isEqualTo(2_000_000L);

		String periods = get("/api/periods").body();
		assertThat(JsonPath.<String>read(periods, "$[0].period")).isEqualTo("2026-06");
		assertThat(JsonPath.<Number>read(periods, "$[0].pendingCount").intValue()).isEqualTo(40);
		assertThat(JsonPath.<Boolean>read(periods, "$[0].batchOpened")).isFalse();

		String relatedParties = get("/api/related-parties").body();
		assertThat(JsonPath.<List<String>>read(relatedParties, "$.groups[0].members[*].agencyId"))
			.containsExactly("D-011", "D-024", "D-037");
		assertThat(JsonPath.<List<Boolean>>read(relatedParties, "$.groups[0].history[*].exceedsLimit")).hasSize(6)
			.containsOnly(true);
		assertThat(JsonPath.<List<Object>>read(relatedParties, "$.nearMisses")).hasSize(1);

		// Submitting June opens the reviews; submitting again is acknowledged without effect.
		assertThat(JsonPath.<Boolean>read(post("/api/settlements", "SALES_SUPPORT", "{\"period\":\"2026-06\"}").body(),
				"$.accepted"))
			.isTrue();
		assertThat(JsonPath.<Boolean>read(post("/api/settlements", "SALES_SUPPORT", "{\"period\":\"2026-06\"}").body(),
				"$.accepted"))
			.isFalse();
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(JsonPath.<List<Object>>read(
					get("/api/approvals/pending?role=SALES_SUPPORT").body(), "$"))
				.hasSize(37));

		String executiveQueue = get("/api/approvals/pending?role=SALES_EXECUTIVE").body();
		assertThat(JsonPath.<List<Object>>read(executiveQueue, "$")).hasSize(1);
		int requestId = JsonPath.<Number>read(executiveQueue, "$[0].id").intValue();
		int reviewId = Integer.parseInt(JsonPath.read(executiveQueue, "$[0].subjectId"));

		String review = get("/api/reviews/" + reviewId).body();
		assertThat(JsonPath.<Number>read(review, "$.totalKrw").longValue()).isEqualTo(3_888_097L);
		assertThat(JsonPath.<List<Object>>read(review, "$.claims")).hasSize(3);
		assertThat(JsonPath.<List<Object>>read(review, "$.evidence.links")).hasSize(3);
		assertThat(JsonPath.<List<Boolean>>read(review,
				"$.evidence.links[?(@.agencyA=='D-024' && @.agencyB=='D-037')].relatedInMasterOnly"))
			.containsExactly(false);

		// The sales support team has no authority over the group's request.
		HttpResponse<String> refused = post("/api/approvals/" + requestId + "/decision", "SALES_SUPPORT",
				"{\"approved\":true,\"comment\":\"전결\"}");
		assertThat(refused.statusCode()).isEqualTo(403);
		assertThat(JsonPath.<String>read(refused.body(), "$.code")).isEqualTo("AUTHORITY");

		// The executive approves, payment goes to the mock bank, and the bank's reply settles the review.
		assertThat(post("/api/approvals/" + requestId + "/decision", "SALES_EXECUTIVE",
				"{\"approved\":true,\"comment\":\"합산 확인\"}")
			.statusCode()).isEqualTo(200);
		// D-024's deposits came from another account, so its payment is held until the agency confirms.
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(JsonPath.<List<Object>>read(get("/api/payments?status=HELD").body(), "$"))
				.hasSize(1));
		assertThat(JsonPath.<List<Object>>read(get("/api/payments?status=SENT").body(), "$")).hasSize(2);
		int clarificationId = JsonPath.<Integer>read(get("/api/payments?status=HELD").body(), "$[0].clarificationId");
		HttpResponse<String> misfit = post("/api/clarifications/" + clarificationId + "/reply", "SALES_SUPPORT",
				"{\"outcome\":\"EXPLAINED\",\"replyText\":\"x\"}");
		assertThat(misfit.statusCode()).isEqualTo(400);
		assertThat(post("/api/clarifications/" + clarificationId + "/reply", "SALES_SUPPORT",
				"{\"outcome\":\"ACCOUNT_CONFIRMED\",\"replyText\":\"등록 계좌가 맞음\"}")
			.statusCode()).isEqualTo(200);
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(JsonPath.<List<Object>>read(get("/api/payments?status=SENT").body(), "$"))
				.hasSize(3));
		assertThat(JsonPath.<Number>read(post("/api/payments/bank-replies", "SALES_SUPPORT", "{}").body(), "$.replied")
			.intValue()).isEqualTo(3);
		await().atMost(Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(JsonPath.<String>read(get("/api/reviews/" + reviewId).body(), "$.status"))
				.isEqualTo("PAID"));
		assertThat(JsonPath.<List<String>>read(get("/api/reviews/" + reviewId).body(), "$.audit[*].action"))
			.contains("REVIEW_OPENED", "APPROVAL_GRANTED", "PAYMENT_SENT", "PAYMENT_CONFIRMED", "REVIEW_PAID");
	}

	@Test
	void publishesTheApiContractAsOpenApi() throws Exception {
		String docs = get("/v3/api-docs").body();

		assertThat(JsonPath.<String>read(docs, "$.info.title")).isEqualTo("OneRoof API");
		assertThat(JsonPath.<java.util.Map<String, Object>>read(docs, "$.paths").keySet()).contains("/api/session",
				"/api/periods", "/api/reviews", "/api/reviews/{id}", "/api/approvals/pending", "/api/payments",
				"/api/audit", "/api/related-parties", "/api/settlements", "/api/approvals/{id}/decision",
				"/api/approvals/decisions", "/api/payments/{id}/bank-reply", "/api/payments/bank-replies",
				"/api/master-changes", "/api/clarifications", "/api/clarifications/{id}/reply", "/api/findings",
				"/api/audit-referrals", "/api/agencies/{id}/identities", "/api/receivables",
				"/api/agencies/{id}/receivables", "/api/agencies/{id}/deposits", "/api/offsets/comparison");
		assertThat(JsonPath.<List<String>>read(docs, "$.paths['/api/settlements'].post.parameters[*].name"))
			.containsExactly("X-Oneroof-Role");
		assertThat(JsonPath.<java.util.Map<String, Object>>read(docs, "$.components.schemas").keySet())
			.contains("ReviewDetailView", "PeriodView", "ApiErrorResponse", "SettlementRequest");
		assertThat(JsonPath.<java.util.Map<String, Object>>read(docs, "$.paths['/api/approvals/{id}/decision'].post.responses")
			.keySet()).contains("200", "403", "409");
		assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
	}

	@Test
	void rejectsMalformedCommandsWithAReadableError() throws Exception {
		HttpResponse<String> badPeriod = post("/api/settlements", "SALES_SUPPORT", "{\"period\":\"June\"}");
		assertThat(badPeriod.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(badPeriod.body(), "$.code")).isEqualTo("REQUEST");

		HttpResponse<String> unknownRole = post("/api/settlements", "AUDITOR", "{\"period\":\"2026-05\"}");
		assertThat(unknownRole.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(unknownRole.body(), "$.message")).contains("AUDITOR");

		HttpResponse<String> sameValue = post("/api/master-changes", "SALES_SUPPORT",
				"{\"agencyId\":\"D-041\",\"kind\":\"ADDRESS\",\"newValue\":\"고양시 중앙로 128\","
						+ "\"effectiveFrom\":\"2026-01-01\",\"reason\":\"x\"}");
		assertThat(sameValue.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(sameValue.body(), "$.message")).contains("같은 값");

		HttpResponse<String> overpaid = post("/api/agencies/D-001/deposits", "FINANCE",
				"{\"amountKrw\":999999999,\"bankAccount\":\"기업 198-22-851114\"}");
		assertThat(overpaid.statusCode()).isEqualTo(400);
		assertThat(JsonPath.<String>read(overpaid.body(), "$.message")).contains("남은 미수금");

		HttpResponse<String> missing = get("/api/reviews/999999");
		assertThat(missing.statusCode()).isEqualTo(404);
		assertThat(JsonPath.<String>read(missing.body(), "$.code")).isEqualTo("NOT_FOUND");
	}

	private HttpResponse<String> get(String path) throws Exception {
		return this.http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String role, String json) throws Exception {
		return this.http.send(HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/json")
			.header("X-Oneroof-Role", role)
			.POST(HttpRequest.BodyPublishers.ofString(json))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}
}
