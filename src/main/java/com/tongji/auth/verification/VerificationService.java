package com.tongji.auth.verification;

import lombok.RequiredArgsConstructor;
import com.tongji.auth.config.AuthProperties;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 验证码业务服务。
 * <p>
 * 负责发送与校验验证码：
 * - 速率限制与日限额；
 * - 随机码生成与存储；
 * - 调用发送器进行实际发送；
 * 配置来源于 `AuthProperties.Verification`。
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final VerificationCodeStore codeStore;
    private final CodeSender codeSender;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties properties;

    /**
     * 发送验证码到指定标识。
     * <p>
     * 执行发送间隔与日次数限制，生成随机数字验证码，保存到存储并调用发送器。
     *
     * @param scene      验证码场景（REGISTER/LOGIN/RESET_PASSWORD）。
     * @param identifier 标识（手机号或邮箱）。
     * @return 发送结果，包含标识、场景与过期秒数。
     * @throws BusinessException 参数不完整或触发速率/日限额时抛出。
     */
    public SendCodeResult sendCode(VerificationScene scene, String identifier) {
        if (scene == null || !StringUtils.hasText(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供正确的验证码发送参数");
        }
        AuthProperties.Verification cfg = properties.getVerification();
        enforceSendInterval(scene, identifier, cfg.getSendInterval());
        enforceDailyLimit(scene, identifier, cfg.getDailyLimit());

        String code = generateNumericCode(cfg.getCodeLength());
        codeStore.saveCode(scene.name(), identifier, code, cfg.getTtl(), cfg.getMaxAttempts());
        codeSender.sendCode(scene, identifier, code, (int) cfg.getTtl().toMinutes());
        return new SendCodeResult(identifier, scene, (int) cfg.getTtl().toSeconds());
    }

    /**
     * 校验验证码是否正确且未超限。
     *
     * @param scene      验证码场景。
     * @param identifier 标识（手机号或邮箱）。
     * @param code       用户输入的验证码。
     * @return 校验结果，包含状态与尝试次数统计。
     * @throws BusinessException 参数不完整时抛出。
     */
    public VerificationCheckResult verify(VerificationScene scene, String identifier, String code) {
        if (scene == null || !StringUtils.hasText(identifier) || !StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码校验参数不完整");
        }
        return codeStore.verify(scene.name(), identifier, code);
    }

    /**
     * 使验证码失效（删除存储记录）。
     *
     * @param scene      验证码场景。
     * @param identifier 标识（手机号或邮箱）。
     */
    public void invalidate(VerificationScene scene, String identifier) {
        codeStore.invalidate(scene.name(), identifier);
    }

    /**
     * 发送间隔限制：同一标识在指定间隔内只能发送一次。
     *
     * @param scene      验证码场景。
     * @param identifier 标识（手机号或邮箱）。
     * @param interval   发送间隔。
     */
    private void enforceSendInterval(VerificationScene scene, String identifier, Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            return;
        }
        String key = "auth:code:last:" + scene.name() + ":" + identifier;
        String existing = stringRedisTemplate.opsForValue().get(key);
        if (existing != null) {
            throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMIT);
        }
        stringRedisTemplate.opsForValue().set(key, "1", interval);
    }

    /**
     * 每日发送次数限制：超过上限则抛出限额异常。
     *
     * @param scene      验证码场景。
     * @param identifier 标识（手机号或邮箱）。
     * @param limit      每日上限次数。
     */
    private void enforceDailyLimit(VerificationScene scene, String identifier, int limit) {
        if (limit <= 0) {
            return;
        }
        String date = DAY_FORMAT.format(LocalDate.now());
        String key = "auth:code:count:" + scene.name() + ":" + identifier + ":" + date;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofDays(1));
        }
        if (count != null && count > limit) {
            throw new BusinessException(ErrorCode.VERIFICATION_DAILY_LIMIT);
        }
    }

    /**
     * 生成指定长度的纯数字验证码。
     *
     * @param length 验证码长度。
     * @return 数字字符串。
     */
    private static String generateNumericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
