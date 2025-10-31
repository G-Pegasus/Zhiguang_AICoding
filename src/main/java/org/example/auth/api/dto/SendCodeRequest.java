package org.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.auth.model.IdentifierType;
import org.example.auth.verification.VerificationScene;

/**
 * 发送验证码请求。
 * <p>
 * `scene` 指定场景（注册/登录/重置密码），配合账号类型与值用于生成并发送验证码。
 */
public record SendCodeRequest(
        @NotNull(message = "场景不能为空") VerificationScene scene,
        @NotNull(message = "账号类型不能为空") IdentifierType identifierType,
        @NotBlank(message = "账号不能为空") String identifier
) {
}
