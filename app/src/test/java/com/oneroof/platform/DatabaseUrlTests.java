package com.oneroof.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseUrlTests {

	@Test
	void convertsPostgresUrlToJdbc() {
		DatabaseUrl url = DatabaseUrl.parse("postgres://app_user:p%40ss@db.internal:6432/appdb?sslmode=disable");

		assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://db.internal:6432/appdb?sslmode=disable");
		assertThat(url.username()).isEqualTo("app_user");
		assertThat(url.password()).isEqualTo("p@ss");
	}

	@Test
	void keepsJdbcUrlUnchanged() {
		DatabaseUrl url = DatabaseUrl.parse("jdbc:postgresql://localhost:5432/oneroof");

		assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/oneroof");
		assertThat(url.username()).isNull();
	}
}
