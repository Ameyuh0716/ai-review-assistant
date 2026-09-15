package com.aiservice.aireviewassistant.actuator;

import com.aiservice.aireviewassistant.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator 端点集成测试。
 *
 * <p>测试目标：验证健康检查、指标、Prometheus 以及 info 等 Actuator 端点
 * 在随机端口启动的 Web 环境中正常暴露，并需要 ADMIN 角色的 JWT 认证。</p>
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorEndpointTest {

    /** 用于发送 HTTP 请求的测试客户端。 */
    @Autowired
    private TestRestTemplate restTemplate;

    /** JWT 配置，用于生成测试用的管理员令牌。 */
    @Autowired
    private JwtProperties jwtProperties;

    /** 持有 ADMIN 角色的访问令牌，供认证请求使用。 */
    private String adminToken;

    /**
     * 每个测试方法执行前的准备。
     *
     * <p>生成一个具有 ADMIN 角色的 JWT Access Token，用于访问受保护的 Actuator 端点。</p>
     */
    @BeforeEach
    void setUp() {
        // 生成 ADMIN 角色的 JWT Access Token
        adminToken = jwtProperties.generateAccessToken(999, "test-admin", "ADMIN");
    }

    /**
     * 构造携带 Authorization 请求头的 HTTP 头对象。
     *
     * @return 包含 Bearer Token 的请求头
     */
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        // 将管理员令牌以 Bearer 形式设置到 Authorization 头
        headers.set("Authorization", "Bearer " + adminToken);
        return headers;
    }

    /**
     * 验证健康检查端点返回 UP 状态。
     *
     * <p>测试场景：以 ADMIN 身份访问 /actuator/health。</p>
     * <p>断言意图：响应状态为 200，且响应体中包含 "status":"UP"。</p>
     */
    @Test
    void healthEndpointShouldReturnUp() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/actuator/health", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        // 验证 HTTP 状态码为 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 验证响应体中应用整体状态为 UP
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    /**
     * 验证指标列表端点可访问。
     *
     * <p>测试场景：以 ADMIN 身份访问 /actuator/metrics。</p>
     * <p>断言意图：响应状态为 200，且返回的指标列表中包含 "names" 字段。</p>
     */
    @Test
    void metricsEndpointShouldBeAccessible() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/actuator/metrics", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        // 验证 HTTP 状态码为 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 验证响应体包含指标名称列表字段
        assertThat(response.getBody()).contains("names");
    }

    /**
     * 验证 Prometheus 端点暴露 JVM 指标。
     *
     * <p>测试场景：以 ADMIN 身份访问 /actuator/prometheus。</p>
     * <p>断言意图：响应状态为 200，且包含 JVM 内存使用指标名称。</p>
     */
    @Test
    void prometheusEndpointShouldExposeMetrics() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        // 验证 HTTP 状态码为 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 验证 Prometheus 格式输出中包含 JVM 内存使用指标
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    /**
     * 验证 info 端点可访问。
     *
     * <p>测试场景：以 ADMIN 身份访问 /actuator/info。</p>
     * <p>断言意图：响应状态为 200。</p>
     */
    @Test
    void infoEndpointShouldBeAccessible() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/actuator/info", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        // 验证 HTTP 状态码为 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
