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

// Actuator 端点集成测试：验证健康检查与指标端点正常暴露
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorEndpointTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtProperties jwtProperties;

    private String adminToken;

    @BeforeEach
    void setUp() {
        // 生成 ADMIN 角色的 JWT Access Token
        adminToken = jwtProperties.generateAccessToken(999, "test-admin", "ADMIN");
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + adminToken);
        return headers;
    }

    @Test
    void healthEndpointShouldReturnUp() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/actuator/health", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void metricsEndpointShouldBeAccessible() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/actuator/metrics", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("names");
    }

    @Test
    void prometheusEndpointShouldExposeMetrics() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/actuator/prometheus", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }

    @Test
    void infoEndpointShouldBeAccessible() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/actuator/info", HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
