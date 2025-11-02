package com.tongji.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.tongji.auth.model.IdentifierType;

/**
 * 登录请求。
 * <p>
 * 支持两种渠道：
 * - 验证码登录：填写 `code`；
 * - 密码登录：填写 `password`（用户已设置时）。
 * `identifierType` 指定账号类型（手机号/邮箱），`identifier` 为账号值。
 */
public record LoginRequest(
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier,
        String code,
        String password
) {
}
