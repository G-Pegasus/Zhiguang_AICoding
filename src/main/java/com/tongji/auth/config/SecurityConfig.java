package com.tongji.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置。
 * <p>
 * - 关闭 CSRF（后端纯 API，使用 JWT 无会话）；
 * - 启用 CORS，当前允许所有来源（后续需替换白名单）；
 * - 无状态会话；
 * - 公开认证相关接口与健康检查，其余接口需鉴权；
 * - 资源服务器启用 JWT 校验。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 配置 Spring Security 过滤链。
     *
     * <p>主要包含：</p>
     * - 关闭 CSRF；
     * - 启用 CORS；
     * - 使用无状态会话策略；
     * - 公开认证接口与健康检查，其余接口需鉴权；
     * - 启用资源服务器的 JWT 校验。
     *
     * @param http Spring 的 {@link HttpSecurity} 构建器。
     * @return 构建完成的 {@link SecurityFilterChain}。
     * @throws Exception 构建过滤链过程中可能抛出的异常。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // 公开内容：首页 Feed 不需要登录
                        .requestMatchers("/api/v1/knowposts/feed").permitAll()
                        // 知文详情（公开已发布内容，非公开由服务层校验）
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/knowposts/detail/*").permitAll()
                        // 知文详情页 RAG 问答（SSE 流式输出）允许匿名访问
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/knowposts/*/qa/stream").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/send-code",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/token/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password/reset"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * 定义并提供 CORS 配置源。
     *
     * <p>当前允许所有来源（后续建议替换为产品白名单），允许常见方法与请求头，且不携带凭证。</p>
     *
     * @return {@link CorsConfigurationSource}，用于为所有路径注册 CORS 规则。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*")); // TODO replace with product whitelist
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
