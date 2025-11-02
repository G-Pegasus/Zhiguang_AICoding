package com.tongji.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.tongji.auth.model.IdentifierType;

/**
 * 注册请求。
 * <p>
 * 字段：账号类型与值、验证码、可选密码、是否同意服务条款。
 * 验证：需通过验证码校验；当提供密码时需通过密码策略校验。
 */
public record RegisterRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        @NotBlank(message = "验证码不能为空") String code,
        String password,
        boolean agreeTerms
) {
}
