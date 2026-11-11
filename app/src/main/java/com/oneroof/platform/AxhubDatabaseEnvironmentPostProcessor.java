package com.oneroof.platform;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Maps the database URLs AXHub injects onto Spring Boot's own properties.
 *
 * <p>{@code DATABASE_URL} goes through a transaction-mode pooler and serves normal queries.
 * {@code DIRECT_DATABASE_URL} goes through a session-mode pooler, which migrations need because
 * Flyway holds a session-level lock while it runs.
 */
public class AxhubDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

	static final String PROPERTY_SOURCE_NAME = "axhubDatabase";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Map<String, Object> properties = new LinkedHashMap<>();
		String queryUrl = environment.getProperty("DATABASE_URL");
		if (queryUrl != null && !queryUrl.isBlank()) {
			DatabaseUrl url = DatabaseUrl.parse(queryUrl);
			properties.put("spring.datasource.url", url.jdbcUrl());
			putIfPresent(properties, "spring.datasource.username", url.username());
			putIfPresent(properties, "spring.datasource.password", url.password());
		}
		String directUrl = environment.getProperty("DIRECT_DATABASE_URL");
		if (directUrl != null && !directUrl.isBlank()) {
			DatabaseUrl url = DatabaseUrl.parse(directUrl);
			properties.put("spring.flyway.url", url.jdbcUrl());
			putIfPresent(properties, "spring.flyway.user", url.username());
			putIfPresent(properties, "spring.flyway.password", url.password());
		}
		if (!properties.isEmpty()) {
			environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
		}
	}

	private static void putIfPresent(Map<String, Object> properties, String key, String value) {
		if (value != null) {
			properties.put(key, value);
		}
	}
}
