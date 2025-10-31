package org.example.auth.verification;

/**
 * 验证码发送器接口。
 * <p>
 * 抽象真实发送行为（短信/邮件/站内），支持不同场景与账号标识。
 * 默认实现可为日志输出，生产环境可替换为第三方服务集成。
 */
public interface CodeSender {

    void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes);
}
