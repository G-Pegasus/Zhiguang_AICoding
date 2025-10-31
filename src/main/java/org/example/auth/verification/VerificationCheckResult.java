package org.example.auth.verification;

/**
 * 验证码校验结果。
 * <p>
 * 包含状态（成功/未找到/过期/错误/尝试过多）和次数统计信息，提供便捷成功判断。
 */
public record VerificationCheckResult(
        VerificationCodeStatus status,
        int attempts,
        int maxAttempts
) {
    public boolean isSuccess() {
        return status == VerificationCodeStatus.SUCCESS;
    }
}

