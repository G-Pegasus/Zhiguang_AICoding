package org.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登出请求。
 * <p>
 * 传入刷新令牌以撤销对应会话，确保令牌不可再用。
 */
public record LogoutRequest(@NotBlank(message = "刷新令牌不能为空") String refreshToken) {
}
