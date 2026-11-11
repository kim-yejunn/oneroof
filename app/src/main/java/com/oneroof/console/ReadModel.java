package com.oneroof.console;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Runs read-only queries against the console views and returns rows keyed in camelCase for JSON. */
@Component
class ReadModel {

	private static final ColumnMapRowMapper CAMEL_CASE = new ColumnMapRowMapper() {
		@Override
		protected String getColumnKey(String columnName) {
			StringBuilder key = new StringBuilder();
			boolean upper = false;
			for (char ch : columnName.toCharArray()) {
				if (ch == '_') {
					upper = true;
				}
				else {
					key.append(upper ? Character.toUpperCase(ch) : ch);
					upper = false;
				}
			}
			return key.toString();
		}
	};

	private final JdbcTemplate jdbc;

	ReadModel(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<Map<String, Object>> rows(String sql, Object... args) {
		return this.jdbc.query(sql, CAMEL_CASE, args);
	}

	Map<String, Object> row(String sql, Object... args) {
		List<Map<String, Object>> rows = rows(sql, args);
		if (rows.isEmpty()) {
			throw new NotFoundException("요청한 항목을 찾을 수 없음");
		}
		return rows.get(0);
	}

	static class NotFoundException extends RuntimeException {

		NotFoundException(String message) {
			super(message);
		}
	}
}
