package com.oneroof.console.web;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Runs read-only queries against the console views and maps each row onto a response record,
 * matching snake_case columns to camelCase components.
 */
@Component
class ReadModel {

	private final JdbcClient jdbc;

	ReadModel(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	<T> List<T> list(Class<T> type, String sql, Object... args) {
		return this.jdbc.sql(sql).params(args).query(type).list();
	}

	<T> T one(Class<T> type, String sql, Object... args) {
		return this.jdbc.sql(sql)
			.params(args)
			.query(type)
			.optional()
			.orElseThrow(() -> new NotFoundException("요청한 항목을 찾을 수 없음"));
	}

	static class NotFoundException extends RuntimeException {

		NotFoundException(String message) {
			super(message);
		}
	}
}
