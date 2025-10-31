package org.example.auth.api.dto;

import java.time.Instant;

/**
 * 令牌响应。
 * <p>
 * 返回访问令牌与刷新令牌及其过期时间，供客户端持久化与后续调用使用。
 */
public record TokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
}
