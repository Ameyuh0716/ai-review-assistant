package com.aiservice.aireviewassistant.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.aiservice.aireviewassistant.config.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 * <p>
 * 继承 {@link OncePerRequestFilter}，确保每次请求仅执行一次。
 * 从请求头中提取 JWT Token 并解析用户身份，构建 Spring Security 认证上下文。
 * </p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;

    /**
     * 构造方法。
     *
     * @param jwtProperties JWT 配置属性，包含请求头名称、Token 解析/校验逻辑
     */
    public JwtAuthenticationFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 核心过滤逻辑：提取、校验 JWT，并设置认证上下文。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 从请求头中获取原始 Authorization 值
        String authHeader = request.getHeader(jwtProperties.getHeaderName());
        // 按 Bearer 协议提取 Token，若格式非法则返回 null
        String token = jwtProperties.extractToken(authHeader);

        // 仅当 Token 存在、有效且为 access token 时才建立认证上下文
        if (token != null) {
            if (jwtProperties.isValid(token) && "access".equals(jwtProperties.getType(token))) {
                // 解析 Token 中的用户身份与角色
                Integer userId = jwtProperties.getUserId(token);
                String username = jwtProperties.getUsername(token);
                String role = jwtProperties.getRole(token);

                // 构建 Spring Security 权限列表，角色统一加上 ROLE_ 前缀
                List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
                );
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, username, authorities);
                // 将认证对象存入 SecurityContext，供后续权限控制使用
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 将当前用户身份写入 request attribute，便于 Controller 层直接获取
                request.setAttribute("currentUserId", userId);
                request.setAttribute("currentUsername", username);
            }
        }

        // 继续执行后续过滤器链
        filterChain.doFilter(request, response);
    }
}
