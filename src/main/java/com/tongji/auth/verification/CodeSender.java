package com.tongji.auth.verification;

/**
 * 验证码发送器接口。
 * <p>
 * 抽象真实发送行为（短信/邮件/站内），支持不同场景与账号标识。
 * 默认实现可为日志输出，生产环境可替换为第三方服务集成。
 */
public interface CodeSender {

    /**
     * 发送验证码到指定标识。
     *
     * @param scene         验证码场景（REGISTER/LOGIN/RESET_PASSWORD）。
     * @param identifier    标识（手机号或邮箱）。
     * @param code          验证码内容。
     * @param expireMinutes 验证码有效期（分钟）。
     */
    void sendCode(VerificationScene scene, String identifier, String code, int expireMinutes);
}
