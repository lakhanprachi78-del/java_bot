package com.ugrocapital.appstatus.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builds the main application DataSource (Postgres, dmcredit schema) from
 * DATABASE_URL exactly as it already sits in your .env — e.g.
 *   postgresql://username:password@host:5432/databasename
 *
 * That's SQLAlchemy/libpq style, not JDBC style, so it can't be handed to
 * Spring's spring.datasource.url as-is; this bean does the one conversion
 * needed (-> jdbc:postgresql://host:5432/databasename + username/password)
 * so nothing about your .env has to change.
 */
@Configuration
public class DataSourceConfig {

    private final AppProperties appProperties;

    @Autowired
    public DataSourceConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public DataSource dataSource() {
        String raw = appProperties.getDatabaseUrl();

        // Accept both "postgresql://" and "postgres://" schemes.
        String normalized = raw.startsWith("postgres://")
                ? raw.replaceFirst("^postgres://", "postgresql://")
                : raw;

        try {
            URI uri = new URI(normalized);
            String userInfo = uri.getUserInfo();
            String username = null;
            String password = null;
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts.length > 1 ? parts[1] : null;
            }
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath(); // "/databasename"

            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;

            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl);
            if (username != null) ds.setUsername(username);
            if (password != null) ds.setPassword(password);
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setPoolName("appstatus-postgres-pool");
            return ds;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "DATABASE_URL is not a valid connection string: " + raw, e);
        }
    }
}
