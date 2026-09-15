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

/**
 * 用户认证控制器。
 * <p>处理注册、登录、Access Token 刷新、获取当前用户信息等认证相关 REST 请求，
 * 基础路径为 /api/auth。</p>
 */
@Tag(name = "用户认证", description = "注册、登录、Token 刷新")
@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserService appUserService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    /**
     * 通过依赖注入构造认证控制器。
     *
     * @param appUserService 用户服务
     * @param jwtProperties JWT 配置与 Token 生成校验工具
     * @param passwordEncoder 密码加密器
     */
    public AuthController(AppUserService appUserService, JwtProperties jwtProperties,
                          PasswordEncoder passwordEncoder) {
        this.appUserService = appUserService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册。
     * <p>POST /api/auth/register</p>
     *
     * @param request 注册请求参数
     * @return 包含登录响应（含 Access Token、Refresh Token 及用户信息）的接口响应
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@RequestBody @Validated RegisterRequest request) {
        AppUser user = appUserService.register(request.getUsername(), request.getPassword(), request.getNickname());
        return ApiResponse.success(buildLoginResponse(user));
    }

    /**
     * 用户登录。
     * <p>POST /api/auth/login，校验用户名与密码后返回 Token 信息。</p>
     *
     * @param request 登录请求参数
     * @return 登录成功返回 Token 信息；失败返回 401 错误
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Validated LoginRequest request) {
        AppUser user = appUserService.findByUsername(request.getUsername());
        // 校验用户是否存在且密码匹配
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        return ApiResponse.success(buildLoginResponse(user));
    }

    /**
     * 刷新 Access Token。
     * <p>POST /api/auth/refresh，使用 Refresh Token 换取新的 Access Token。</p>
     *
     * @param refreshToken 刷新令牌
     * @return 刷新成功返回新 Token；Token 无效或用户不存在返回 401 错误
     */
    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestParam String refreshToken) {
        // 校验 Refresh Token 是否有效
        if (!jwtProperties.isValidRefreshToken(refreshToken)) {
            return ApiResponse.error(401, "Refresh Token 无效或已过期，请重新登录");
        }
        // 从 Refresh Token 解析用户 ID，并校验用户是否存在
        Integer userId = jwtProperties.getUserId(refreshToken);
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        return ApiResponse.success(buildLoginResponse(user));
    }

    /**
     * 获取当前登录用户信息。
     * <p>GET /api/auth/me，需已登录并携带有效 Token。</p>
     *
     * @param userId 当前用户 ID（由认证拦截器写入请求属性）
     * @return 当前用户信息；未登录返回 401，用户不存在返回 404
     */
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(
            @RequestAttribute(value = "currentUserId", required = false) Integer userId) {
        // 未携带用户 ID 表示未登录
        if (userId == null) {
            return ApiResponse.error(401, "未登录，请先登录");
        }
        AppUser user = appUserService.getById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        // 仅返回用户信息，不重新生成 Token（Token 字段置空）
        return ApiResponse.success(new LoginResponse(null, null, user.getId(), user.getUsername(),
                user.getNickname(), user.getRole(), 0));
    }

    /**
     * 构建登录响应，生成 Access Token 与 Refresh Token。
     *
     * @param user 登录用户
     * @return 包含双 Token、有效期及用户信息的响应对象
     */
    private LoginResponse buildLoginResponse(AppUser user) {
        String accessToken = jwtProperties.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtProperties.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(accessToken, refreshToken, user.getId(), user.getUsername(),
                user.getNickname(), user.getRole(), jwtProperties.getAccessExpirationMs());
    }
}
