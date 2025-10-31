package org.example.auth.api.dto;

/**
 * 认证用户响应。
 * <p>
 * 面向客户端展示的基础用户信息，供“我是谁”与首页显示使用。
 */
public record AuthUserResponse(
        Long id,
        String nickname,
        String avatar,
        String phone,
        String email
) {
}
