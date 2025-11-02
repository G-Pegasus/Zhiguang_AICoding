package com.tongji.auth.token;

import java.time.Duration;

/**
 * 刷新令牌白名单存储接口。
 * <p>
 * 负责管理 Refresh Token 的有效性：存储、校验、撤销单个令牌与撤销用户全部令牌。
 * 实现可使用 Redis、数据库或其它持久化方案。
 */
public interface RefreshTokenStore {

    /**
     * 存储刷新令牌白名单记录。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID（jti）。
     * @param ttl     生存时间（过期后自动失效）。
     */
    void storeToken(long userId, String tokenId, Duration ttl);

    /**
     * 校验刷新令牌是否仍然有效（在白名单内且未过期）。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID（jti）。
     * @return 是否有效。
     */
    boolean isTokenValid(long userId, String tokenId);

    /**
     * 撤销指定刷新令牌（从白名单移除）。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID（jti）。
     */
    void revokeToken(long userId, String tokenId);

    /**
     * 撤销用户的所有刷新令牌（强制该用户所有会话下线）。
     *
     * @param userId 用户 ID。
     */
    void revokeAll(long userId);
}

