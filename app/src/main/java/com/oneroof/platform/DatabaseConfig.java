package com.oneroof.platform;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseConfig {

	/**
	 * AXHub routes DATABASE_URL through a transaction-mode pooler, which cannot keep server-side
	 * prepared statements, so the driver is told never to promote statements to prepared ones.
	 */
	@Bean
	@ConditionalOnProperty("DATABASE_URL")
	DataSource dataSource(Environment environment) {
		DatabaseUrl url = DatabaseUrl.parse(environment.getRequiredProperty("DATABASE_URL"));
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(url.jdbcUrl());
		if (url.username() != null) {
			config.setUsername(url.username());
			config.setPassword(url.password());
		}
		config.setMaximumPoolSize(5);
		config.setInitializationFailTimeout(-1);
		config.addDataSourceProperty("prepareThreshold", "0");
		return new HikariDataSource(config);
	}
}
