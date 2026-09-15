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

/**
 * JWT 配置属性与令牌工具类。
 * <p>绑定前缀为 {@code app.jwt} 的配置项，支持 Access Token + Refresh Token 双令牌机制。
 * 提供令牌生成、解析、校验以及从请求头中提取令牌的能力。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** 签名密钥，要求至少 32 字节（用于 HMAC-SHA 签名）。生产环境应通过外部化配置覆盖默认值。 */
    private String secret = "ai-review-assistant-jwt-secret-key-2024-very-long-string";

    /** Access Token 有效期，默认 15 分钟（单位：毫秒）。 */
    private long accessExpirationMs = 15 * 60 * 1000L;

    /** Refresh Token 有效期，默认 7 天（单位：毫秒）。 */
    private long refreshExpirationMs = 7 * 24 * 60 * 60 * 1000L;

    /** Token 前缀，默认 {@code "Bearer "}。 */
    private String tokenPrefix = "Bearer ";

    /** Authorization 请求头名称，默认 {@code "Authorization"}。 */
    private String headerName = "Authorization";

    /**
     * 根据配置的 secret 生成 HMAC-SHA 签名密钥。
     *
     * @return 用于签名和验证 JWT 的 SecretKey
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ===== Access Token =====

    /**
     * 生成 Access Token。
     * <p>Access Token 用于接口访问鉴权，有效期由 {@link #accessExpirationMs} 决定（默认 15 分钟）。</p>
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     用户角色
     * @return 生成的 Access Token 字符串
     */
    public String generateAccessToken(Integer userId, String username, String role) {
        return generateToken(userId, username, role, accessExpirationMs, "access");
    }

    // ===== Refresh Token =====

    /**
     * 生成 Refresh Token。
     * <p>Refresh Token 用于在 Access Token 过期后换取新的 Access Token，
     * 有效期由 {@link #refreshExpirationMs} 决定（默认 7 天）。</p>
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     用户角色
     * @return 生成的 Refresh Token 字符串
     */
    public String generateRefreshToken(Integer userId, String username, String role) {
        return generateToken(userId, username, role, refreshExpirationMs, "refresh");
    }

    // ===== 通用方法 =====

    /**
     * 通用令牌生成方法。
     * <p>将用户ID、用户名、角色和令牌类型写入声明，并使用 HMAC-SHA 签名。</p>
     *
     * @param userId       用户 ID
     * @param username     用户名
     * @param role         用户角色
     * @param expirationMs 令牌过期时间（毫秒）
     * @param type         令牌类型（{@code access} 或 {@code refresh}）
     * @return 签名后的 JWT 字符串
     */
    private String generateToken(Integer userId, String username, String role, long expirationMs, String type) {
        Date now = new Date();
        // 根据传入的有效期计算过期时间
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

    /**
     * 解析 Token 并返回其中的声明（Claims）。
     *
     * @param token 待解析的 JWT 字符串
     * @return JWT 声明体
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户 ID。
     *
     * @param token JWT 字符串
     * @return 用户 ID
     */
    public Integer getUserId(String token) {
        return Integer.parseInt(parseToken(token).getSubject());
    }

    /**
     * 从 Token 中提取用户名。
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /**
     * 从 Token 中提取角色。
     *
     * @param token JWT 字符串
     * @return 用户角色
     */
    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * 获取 Token 类型。
     *
     * @param token JWT 字符串
     * @return 令牌类型，{@code access} 或 {@code refresh}
     */
    public String getType(String token) {
        return parseToken(token).get("type", String.class);
    }

    /**
     * 验证 Token 是否有效（未过期且签名正确）。
     *
     * @param token JWT 字符串
     * @return 有效返回 {@code true}，否则返回 {@code false}
     */
    public boolean isValid(String token) {
        try {
            Claims claims = parseToken(token);
            // 判断过期时间是否在当前时间之前
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证是否为有效的 Refresh Token。
     * <p>除基础有效性校验外，还会检查 Token 类型声明是否为 {@code refresh}。</p>
     *
     * @param token JWT 字符串
     * @return 是有效 Refresh Token 返回 {@code true}，否则返回 {@code false}
     */
    public boolean isValidRefreshToken(String token) {
        try {
            return isValid(token) && "refresh".equals(getType(token));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从完整的 Authorization 请求头中提取纯 Token。
     *
     * @param authHeader 请求头中的 Authorization 值
     * @return 去掉 Token 前缀后的纯 Token；若格式不匹配则返回 {@code null}
     */
    public String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith(tokenPrefix)) {
            return authHeader.substring(tokenPrefix.length());
        }
        return null;
    }
}
