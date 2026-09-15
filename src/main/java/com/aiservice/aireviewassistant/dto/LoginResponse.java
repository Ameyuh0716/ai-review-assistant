package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录成功响应 DTO。
 * <p>认证成功后返回给前端，包含访问令牌、刷新令牌以及当前用户基本信息。</p>
 */
@Getter
@Setter
public class LoginResponse {

    /** 访问令牌（Access Token），用于后续请求的认证鉴权。 */
    private String token;

    /** 刷新令牌（Refresh Token），用于 Access Token 过期后换取新的令牌。 */
    private String refreshToken;

    /** 当前登录用户 ID。 */
    private Integer userId;

    /** 当前登录用户名。 */
    private String username;

    /** 当前登录用户昵称。 */
    private String nickname;

    /** 当前登录用户角色，例如 student、admin 等。 */
    private String role;

    /** Access Token 过期时间，单位：毫秒；前端可据此自动刷新令牌。 */
    private long expiresIn;

    /**
     * 全参构造函数。
     *
     * @param token       访问令牌
     * @param refreshToken 刷新令牌
     * @param userId      用户 ID
     * @param username    用户名
     * @param nickname    用户昵称
     * @param role        用户角色
     * @param expiresIn   Access Token 过期时间（毫秒）
     */
    public LoginResponse(String token, String refreshToken, Integer userId,
                         String username, String nickname, String role, long expiresIn) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.expiresIn = expiresIn;
    }
}
