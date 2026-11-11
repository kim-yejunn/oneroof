package com.oneroof.platform;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Deployment checks: the health probe target and how the platform database is wired. */
@io.swagger.v3.oas.annotations.tags.Tag(name = "플랫폼", description = "배포 점검용")
@RestController
public class PlatformCheckController {

	private final DataSource dataSource;
	private final Environment environment;

	public PlatformCheckController(DataSource dataSource, Environment environment) {
		this.dataSource = dataSource;
		this.environment = environment;
	}

	/** Health probe target: answers without touching the database. */
	@GetMapping(value = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
	String healthz() {
		return "ok";
	}

	/** Reports how the platform database is wired, without exposing credentials. */
	@GetMapping("/api/platform-check")
	Map<String, Object> platformCheck() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("javaVersion", Runtime.version().toString());
		result.put("maxMemoryMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
		result.put("processors", Runtime.getRuntime().availableProcessors());
		result.put("databaseUrl", describe("DATABASE_URL"));
		result.put("directDatabaseUrl", describe("DIRECT_DATABASE_URL"));

		try (Connection connection = this.dataSource.getConnection()) {
			result.put("databaseProduct", connection.getMetaData().getDatabaseProductVersion());
			int repeats = 0;
			for (int i = 0; i < 8; i++) {
				try (PreparedStatement statement = connection.prepareStatement("select ?::int + 1");) {
					statement.setInt(1, i);
					try (ResultSet rows = statement.executeQuery()) {
						rows.next();
						repeats += rows.getInt(1) == i + 1 ? 1 : 0;
					}
				}
			}
			result.put("repeatedParameterizedQueriesOk", repeats);
			result.put("database", "ok");
		}
		catch (Exception ex) {
			result.put("database", "error");
			result.put("databaseError", ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
		return result;
	}

	private Map<String, Object> describe(String name) {
		Map<String, Object> info = new LinkedHashMap<>();
		String raw = environment.getProperty(name);
		info.put("present", raw != null);
		if (raw == null) {
			return info;
		}
		try {
			URI uri = URI.create(raw.startsWith("jdbc:") ? raw.substring(5) : raw);
			info.put("scheme", raw.startsWith("jdbc:") ? "jdbc:" + uri.getScheme() : uri.getScheme());
			info.put("port", uri.getPort());
			info.put("hasCredentials", uri.getRawUserInfo() != null);
			info.put("query", uri.getRawQuery());
		}
		catch (IllegalArgumentException ex) {
			info.put("scheme", "unparseable");
		}
		return info;
	}
}
