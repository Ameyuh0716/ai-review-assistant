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

// JWT 认证过滤器：从请求头中提取并验证 JWT Token
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;

    public JwtAuthenticationFilter(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(jwtProperties.getHeaderName());
        String token = jwtProperties.extractToken(authHeader);

        if (token != null) {
            // 仅处理 access token，refresh token 不用于认证
            if (jwtProperties.isValid(token) && "access".equals(jwtProperties.getType(token))) {
                Integer userId = jwtProperties.getUserId(token);
                String username = jwtProperties.getUsername(token);
                String role = jwtProperties.getRole(token);

                // 构建认证对象，放入 SecurityContext
                List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
                );
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, username, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 将 userId 存入 request attribute，方便 Controller 获取
                request.setAttribute("currentUserId", userId);
                request.setAttribute("currentUsername", username);
            }
        }

        filterChain.doFilter(request, response);
    }
}
