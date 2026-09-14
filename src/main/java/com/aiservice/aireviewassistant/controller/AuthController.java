package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.config.JwtProperties;
import com.aiservice.aireviewassistant.dto.LoginRequest;
import com.aiservice.aireviewassistant.dto.LoginResponse;
import com.aiservice.aireviewassistant.dto.RegisterRequest;
import com.aiservice.aireviewassistant.entity.AppUser;
import com.aiservice.aireviewassistant.service.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// 认证控制器：注册、登录、刷新 Token、获取当前用户信息
@Tag(name = "用户认证", description = "注册、登录、Token 刷新")
@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserService appUserService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AppUserService appUserService, JwtProperties jwtProperties,
                          PasswordEncoder passwordEncoder) {
        this.appUserService = appUserService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    // 用户注册
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@RequestBody @Validated RegisterRequest request) {
        AppUser user = appUserService.register(request.getUsername(), request.getPassword(), request.getNickname());
        return ApiResponse.success(buildLoginResponse(user));
    }

    // 用户登录
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Validated LoginRequest request) {
        AppUser user = appUserService.findByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        return ApiResponse.success(buildLoginResponse(user));
    }

    // 刷新 Access Token（用 Refresh Token 换取新的 Access Token）
    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestParam String refreshToken) {
        if (!jwtProperties.isValidRefreshToken(refreshToken)) {
            return ApiResponse.error(401, "Refresh Token 无效或已过期，请重新登录");
        }
        Integer userId = jwtProperties.getUserId(refreshToken);
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        return ApiResponse.success(buildLoginResponse(user));
    }

    // 获取当前登录用户信息（需要 Token）
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(
            @RequestAttribute(value = "currentUserId", required = false) Integer userId) {
        if (userId == null) {
            return ApiResponse.error(401, "未登录，请先登录");
        }
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        return ApiResponse.success(new LoginResponse(null, null, user.getId(), user.getUsername(),
                user.getNickname(), user.getRole(), 0));
    }

    // 构建登录响应（生成 Access Token + Refresh Token）
    private LoginResponse buildLoginResponse(AppUser user) {
        String accessToken = jwtProperties.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtProperties.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(accessToken, refreshToken, user.getId(), user.getUsername(),
                user.getNickname(), user.getRole(), jwtProperties.getAccessExpirationMs());
    }
}
