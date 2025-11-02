package com.tongji.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.tongji.auth.model.IdentifierType;

/**
 * 重置密码请求。
 * <p>
 * 通过验证码校验身份后设置新密码。密码需满足复杂度策略。
 */
public record PasswordResetRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        @NotBlank(message = "验证码不能为空") String code,
        @NotBlank(message = "新密码不能为空") String newPassword
) {
}
