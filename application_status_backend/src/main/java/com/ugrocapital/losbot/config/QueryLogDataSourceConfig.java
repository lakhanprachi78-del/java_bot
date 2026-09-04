package com.ugrocapital.losbot.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class QueryLogDataSourceConfig {

    @Bean(name = "queryLogDataSource")
    public DataSource queryLogDataSource(@Value("${los.logs.query-db:los_query_log.db}") String database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl(database.startsWith("jdbc:") ? database : "jdbc:sqlite:" + database);
        return dataSource;
    }

    @Bean(name = "queryLogJdbcTemplate")
    public JdbcTemplate queryLogJdbcTemplate(@Qualifier("queryLogDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}