package com.aiservice.aireviewassistant.config;

import com.aiservice.aireviewassistant.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置类。
 * <p>配置 JWT 无状态认证、接口权限控制、密码加密器以及禁用 Session 和 CSRF。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtProperties jwtProperties;

    /**
     * 构造安全配置类。
     *
     * @param jwtProperties JWT 配置属性，用于构建认证过滤器
     */
    public SecurityConfig(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 配置 {@link SecurityFilterChain}。
     * <p>规则说明：
     * <ul>
     *   <li>禁用 CSRF（前后端分离 / JWT 无状态场景）</li>
     *   <li>Session 策略设置为 STATELESS</li>
     *   <li>在 UsernamePasswordAuthenticationFilter 之前添加 JWT 认证过滤器</li>
     *   <li>公开静态资源、认证接口、Swagger、Actuator 健康检查</li>
     *   <li>Actuator 其他端点需要 ADMIN 角色</li>
     *   <li>Agent 与对话接口允许匿名访问（登录后自动关联用户）</li>
     *   <li>其余请求均需认证</li>
     * </ul></p>
     *
     * @param http Spring Security 配置构建器
     * @return 构建好的 SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new JwtAuthenticationFilter(jwtProperties), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 公开静态资源、首页
                .requestMatchers("/", "/index.html", "/static/**", "/css/**", "/*.html").permitAll()
                // 公开认证接口（注册/登录/刷新Token）
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh").permitAll()
                // 公开 Swagger / OpenAPI
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                // 公开 Actuator 健康检查
                .requestMatchers("/actuator/health").permitAll()
                // 需要 ADMIN 角色
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // 对话和课程接口允许匿名（登录后自动关联用户）
                .requestMatchers("/api/agent/**", "/api/conversations/**").permitAll()
                // 默认：所有其他请求需要认证
                .anyRequest().authenticated()
            );
        return http.build();
    }

    /**
     * 注册密码编码器 Bean。
     * <p>使用 BCrypt 强哈希算法对用户密码进行加密与校验。</p>
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
