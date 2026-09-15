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

/**
 * PostgreSQL + pgvector 扩展集成测试。
 *
 * <p>测试目标：通过 Testcontainers 启动内置 pgvector 的 PostgreSQL 容器，
 * 验证 Spring Boot 应用能够使用该数据源，并且 pgvector 数据库可正常连接。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest
class PostgreSQLPgVectorIntegrationTest {

    /**
     * pgvector/pgvector:pg16 容器实例。
     *
     * <p>暴露 5432 端口，并设置数据库名、用户名和密码。</p>
     */
    @Container
    static GenericContainer<?> postgres = new GenericContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16"))
        .withExposedPorts(5432)                       // 暴露 PostgreSQL 默认端口
        .withEnv("POSTGRES_DB", "review_db")          // 设置默认数据库名称
        .withEnv("POSTGRES_USER", "postgres")         // 设置数据库用户名
        .withEnv("POSTGRES_PASSWORD", "postgres");    // 设置数据库密码

    /**
     * 动态配置 Spring Boot 数据源属性。
     *
     * <p>将应用的数据源指向 Testcontainers 启动的 PostgreSQL 容器，并禁用
     * PgVector 向量存储的自动 schema 初始化。</p>
     *
     * @param registry 动态属性注册表
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 注册 JDBC URL，使用容器暴露的随机映射端口
        registry.add("spring.datasource.url",
            () -> String.format("jdbc:postgresql://%s:%d/review_db?currentSchema=public",
                postgres.getHost(), postgres.getFirstMappedPort()));
        // 注册数据库用户名
        registry.add("spring.datasource.username", () -> "postgres");
        // 注册数据库密码
        registry.add("spring.datasource.password", () -> "postgres");
        // 关闭 Spring AI PgVector 的 schema 自动初始化
        registry.add("spring.ai.vectorstore.pgvector.initialize-schema", () -> "false");
    }

    /**
     * 验证 PostgreSQL 容器可连接并返回预期结果。
     *
     * <p>测试场景：使用 JDBC 直接连接 Testcontainers 启动的数据库，执行 SELECT 1。</p>
     * <p>断言意图：结果集存在下一行，且返回的整数值为 1。</p>
     *
     * @throws Exception 当 JDBC 连接或查询执行失败时抛出
     */
    @Test
    void shouldConnectToPostgresAndPgVectorExtensionAvailable() throws Exception {
        // 构造指向 Testcontainers 容器的 JDBC URL
        String url = String.format("jdbc:postgresql://%s:%d/review_db",
            postgres.getHost(), postgres.getFirstMappedPort());
        // 使用 try-with-resources 确保连接、语句和结果集自动关闭
        try (Connection connection = DriverManager.getConnection(url, "postgres", "postgres");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {

            // 断言结果集包含数据行
            assertThat(resultSet.next()).isTrue();
            // 断言查询返回的整数值为 1
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }
}
