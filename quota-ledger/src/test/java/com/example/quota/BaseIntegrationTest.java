package com.example.quota;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Boots the real Spring Boot service against a real PostgreSQL server
 * (Zonky embedded distribution, PG 16) so NUMERIC/BigDecimal behaviour,
 * triggers and row locks are exercised exactly as in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    static volatile EmbeddedPostgres PG;
    static volatile int PG_PORT;

    static {
        try {
            PG = EmbeddedPostgres.builder()
                    .setServerConfig("timezone", "UTC")
                    .start();
            PG_PORT = PG.getPort();
            System.setProperty("spring.datasource.url",
                    "jdbc:postgresql://localhost:" + PG_PORT + "/postgres");
            System.setProperty("spring.datasource.username", "postgres");
            System.setProperty("spring.datasource.password", "postgres");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @LocalServerPort
    int port;
}
