package com.tongji.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * 认证相关配置属性，绑定前缀 {@code auth.*}。
 *
 * <p>包含以下分组：</p>
 * - Jwt：令牌签发与验证配置；
 * - Verification：验证码发送与校验配置；
 * - Password：密码策略与加密强度配置。
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** JWT 配置项。 */
    private final Jwt jwt = new Jwt();
    /** 验证码配置项。 */
    private final Verification verification = new Verification();
    /** 密码策略配置项。 */
    private final Password password = new Password();

    @Data
    public static class Jwt {
        /** JWT 签发者标识（iss）。 */
        private String issuer = "zhiguang";
        /** 访问令牌有效期（TTL）。 */
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        /** 刷新令牌有效期（TTL）。 */
        private Duration refreshTokenTtl = Duration.ofDays(7);
        /** JWK 密钥标识（kid），用于下游校验与轮换。 */
        private String keyId = "zhiguang-key";
        /** RSA 私钥 PEM（PKCS#8）资源。 */
        private Resource privateKey;
        /** RSA 公钥 PEM（X.509）资源。 */
        private Resource publicKey;
    }

    /**
     * 验证码配置：位数、有效期、最大尝试次数、发送间隔与每日上限。
     */
    @Data
    public static class Verification {
        /** 验证码位数。 */
        private int codeLength = 6;
        /** 验证码有效时间。 */
        private Duration ttl = Duration.ofMinutes(5);
        /** 最大校验尝试次数。 */
        private int maxAttempts = 5;
        /** 同标识连续发送的最小间隔。 */
        private Duration sendInterval = Duration.ofSeconds(60);
        /** 同标识每日发送上限。 */
        private int dailyLimit = 10;
    }

    /** 密码策略配置。 */
    @Data
    public static class Password {
        /** 密码哈希强度（BCrypt cost）。 */
        private int bcryptStrength = 12;
        /** 密码最小长度。 */
        private int minLength = 8;
    }
}
