package com.oneroof.platform;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Converts a {@code postgres://user:pass@host:port/db?params} URL, the form AXHub injects as
 * DATABASE_URL, into the pieces a JDBC driver needs. A URL that already starts with
 * {@code jdbc:} is passed through unchanged.
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

	public static DatabaseUrl parse(String raw) {
		if (raw.startsWith("jdbc:")) {
			return new DatabaseUrl(raw, null, null);
		}
		URI uri = URI.create(raw);
		String username = null;
		String password = null;
		String userInfo = uri.getRawUserInfo();
		if (userInfo != null) {
			int colon = userInfo.indexOf(':');
			username = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
			password = colon < 0 ? null : decode(userInfo.substring(colon + 1));
		}
		StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
		if (uri.getPort() > 0) {
			jdbc.append(':').append(uri.getPort());
		}
		jdbc.append(uri.getRawPath() == null ? "/" : uri.getRawPath());
		if (uri.getRawQuery() != null) {
			jdbc.append('?').append(uri.getRawQuery());
		}
		return new DatabaseUrl(jdbc.toString(), username, password);
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
