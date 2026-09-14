package com.aiservice.aireviewassistant.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// JWT 配置与工具类：支持 Access Token + Refresh Token 双令牌机制
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    // 签名密钥（至少32字节）
    private String secret = "ai-review-assistant-jwt-secret-key-2024-very-long-string";

    // Access Token 有效期（默认15分钟）
    private long accessExpirationMs = 15 * 60 * 1000L;

    // Refresh Token 有效期（默认7天）
    private long refreshExpirationMs = 7 * 24 * 60 * 60 * 1000L;

    // Token 前缀
    private String tokenPrefix = "Bearer ";

    // Authorization Header 名称
    private String headerName = "Authorization";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ===== Access Token =====

    public String generateAccessToken(Integer userId, String username, String role) {
        return generateToken(userId, username, role, accessExpirationMs, "access");
    }

    // ===== Refresh Token =====

    public String generateRefreshToken(Integer userId, String username, String role) {
        return generateToken(userId, username, role, refreshExpirationMs, "refresh");
    }

    // ===== 通用方法 =====

    private String generateToken(Integer userId, String username, String role, long expirationMs, String type) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    // 解析 Token，返回 Claims
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 从 Token 中提取用户ID
    public Integer getUserId(String token) {
        return Integer.parseInt(parseToken(token).getSubject());
    }

    // 从 Token 中提取用户名
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    // 从 Token 中提取角色
    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    // 获取 Token 类型（access / refresh）
    public String getType(String token) {
        return parseToken(token).get("type", String.class);
    }

    // 验证 Token 是否有效（未过期）
    public boolean isValid(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    // 验证是否为有效的 Refresh Token
    public boolean isValidRefreshToken(String token) {
        try {
            return isValid(token) && "refresh".equals(getType(token));
        } catch (Exception e) {
            return false;
        }
    }

    // 从完整 Authorization Header 中提取纯 Token
    public String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith(tokenPrefix)) {
            return authHeader.substring(tokenPrefix.length());
        }
        return null;
    }
}
