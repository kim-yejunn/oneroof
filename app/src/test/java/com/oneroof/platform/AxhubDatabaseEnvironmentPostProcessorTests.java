package com.oneroof.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class AxhubDatabaseEnvironmentPostProcessorTests {

	private final AxhubDatabaseEnvironmentPostProcessor postProcessor = new AxhubDatabaseEnvironmentPostProcessor();

	@Test
	void mapsQueryUrlToDataSourceAndDirectUrlToFlyway() {
		StandardEnvironment environment = environmentWith(Map.of(
				"DATABASE_URL", "postgres://app:secret@pooler:6432/appdb?sslmode=disable",
				"DIRECT_DATABASE_URL", "postgres://app:secret@pooler:6433/appdb?sslmode=disable"));

		this.postProcessor.postProcessEnvironment(environment, null);

		assertThat(environment.getProperty("spring.datasource.url"))
			.isEqualTo("jdbc:postgresql://pooler:6432/appdb?sslmode=disable");
		assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("app");
		assertThat(environment.getProperty("spring.flyway.url"))
			.isEqualTo("jdbc:postgresql://pooler:6433/appdb?sslmode=disable");
		assertThat(environment.getProperty("spring.flyway.user")).isEqualTo("app");
		assertThat(environment.getProperty("spring.flyway.password")).isEqualTo("secret");
	}

	@Test
	void leavesLocalSettingsAloneWhenNothingIsInjected() {
		StandardEnvironment environment = environmentWith(Map.of("spring.datasource.url", "jdbc:postgresql://localhost/dev"));

		this.postProcessor.postProcessEnvironment(environment, null);

		assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost/dev");
		assertThat(environment.getPropertySources().contains(AxhubDatabaseEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
			.isFalse();
	}

	private static StandardEnvironment environmentWith(Map<String, Object> properties) {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addLast(new MapPropertySource("test", properties));
		return environment;
	}
}
