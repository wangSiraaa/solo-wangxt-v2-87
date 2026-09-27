package com.example.quota.db;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * Applies schema.sql / data.sql as whole scripts through the PostgreSQL
 * wire protocol (single Statement.execute), so the server parses dollar-quoted
 * ($$ ... $$) PL/pgSQL trigger bodies natively. Spring's default script
 * initializer splits on ';' and cannot handle those function bodies.
 * Everything is idempotent (IF NOT EXISTS / ON CONFLICT), so restarts are safe.
 */
@Component
public class DatabaseInitializer {

    private final JdbcTemplate jdbc;

    @Value("${ledger.demo-seed:true}")
    private boolean demoSeed;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void initialize() throws Exception {
        runScript("db/schema.sql");
        if (demoSeed) {
            runScript("db/data.sql");
        }
    }

    private void runScript(String path) throws Exception {
        String sql = StreamUtils.copyToString(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        // Whole-script execution: PG server handles $$ quoting and multiple statements.
        jdbc.execute(sql);
    }
}
