package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

// 登录成功响应 DTO（包含 Access Token + Refresh Token）
@Getter
@Setter
public class LoginResponse {

    private String token;
    private String refreshToken;
    private Integer userId;
    private String username;
    private String nickname;
    private String role;
    // Access Token 过期时间（毫秒），前端据此自动刷新
    private long expiresIn;

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
