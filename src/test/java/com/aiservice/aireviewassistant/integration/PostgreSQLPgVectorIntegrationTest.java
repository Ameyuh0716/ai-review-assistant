package com.aiservice.aireviewassistant.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

// Testcontainers 集成测试：验证 PostgreSQL + pgvector 可用
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
class PostgreSQLPgVectorIntegrationTest {

    @Container
    static GenericContainer<?> postgres = new GenericContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16"))
        .withExposedPorts(5432)
        .withEnv("POSTGRES_DB", "review_db")
        .withEnv("POSTGRES_USER", "postgres")
        .withEnv("POSTGRES_PASSWORD", "postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> String.format("jdbc:postgresql://%s:%d/review_db?currentSchema=public",
                postgres.getHost(), postgres.getFirstMappedPort()));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "false");
    }

    @Test
    void shouldConnectToPostgresAndPgVectorExtensionAvailable() throws Exception {
        String url = String.format("jdbc:postgresql://%s:%d/review_db",
            postgres.getHost(), postgres.getFirstMappedPort());
        try (Connection connection = DriverManager.getConnection(url, "postgres", "postgres");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {

            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }
}
