package com.tongji.auth.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 开发/测试用验证码发送器。
 * <p>
 * 不实际发送，仅记录日志，便于本地开发与集成测试。
 */
@Slf4j
@Component
public class LoggingCodeSender implements CodeSender {

    /**
     * 记录验证码发送信息到日志（不实际发送）。
     *
     * @param scene         验证码场景。
     * @param identifier    标识（手机号或邮箱）。
     * @param code          验证码内容。
     * @param expireMinutes 有效期（分钟）。
     */
    @Override
    public void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes) {
        log.info("Send verification code scene={} identifier={} code={} expireMinutes={}", scene, identifier, code, expireMinutes);
    }
}
