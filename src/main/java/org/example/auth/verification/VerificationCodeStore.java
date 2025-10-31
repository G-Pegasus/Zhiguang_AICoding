package org.example.auth.verification;

import java.time.Duration;

/**
 * 验证码存储接口。
 * <p>
 * 抽象验证码的保存、校验与失效操作，允许使用 Redis 等实现。
 * 需支持最大尝试次数与 TTL 以保证安全性。
 */
public interface VerificationCodeStore {

    void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts);

    VerificationCheckResult verify(String scene, String identifier, String code);

    void invalidate(String scene, String identifier);
}

