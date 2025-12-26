package com.tongji.auth.verification;

/**
 * 发送验证码结果。
 * <p>
 * 返回规范化账号、发送场景与验证码有效期（秒）。
 */
public record SendCodeResult(String identifier,
                             VerificationScene scene,
                             int expireSeconds
) {
}
