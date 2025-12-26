package com.tongji.profile.api;

import com.tongji.profile.api.dto.ProfilePatchRequest;
import com.tongji.profile.api.dto.ProfileResponse;
import com.tongji.storage.OssStorageService;
import com.tongji.auth.token.JwtService;
import com.tongji.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人资料接口。
 *
 * <p>负责当前登录用户的资料更新与头像上传。</p>
 * <p>鉴权：依赖 Spring Security Resource Server 注入 {@link Jwt}，并从中解析用户 ID。</p>
 */
@RestController
@RequestMapping("/api/v1/profile")
@Validated
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final JwtService jwtService;
    private final OssStorageService ossStorageService;

    /**
     * 更新个人资料（部分字段 PATCH）。
     *
     * <p>请求体使用 {@link ProfilePatchRequest}，配合 {@link Valid} 做参数校验。</p>
     * <p>用户身份从 {@link Jwt} 中解析，避免前端传入 userId 造成越权。</p>
     *
     * @param jwt 当前请求的 JWT（由认证框架注入）
     * @param request 待更新字段集合（允许部分字段为空）
     * @return 更新后的个人资料快照
     */
    @PatchMapping
    public ProfileResponse patch(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody ProfilePatchRequest request) {
        long userId = jwtService.extractUserId(jwt);

        return profileService.updateProfile(userId, request);
    }

    /**
     * 上传头像并更新用户头像地址。
     *
     * <p>文件先上传到对象存储，由对象存储返回可访问 URL；再将 URL 写回用户资料。</p>
     *
     * @param jwt 当前请求的 JWT（由认证框架注入）
     * @param file 头像文件（multipart/form-data）
     * @return 更新后的个人资料快照（包含新头像 URL）
     */
    @PostMapping("/avatar")
    public ProfileResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt,
                                        @RequestPart("file") MultipartFile file) {
        long userId = jwtService.extractUserId(jwt);
        String url = ossStorageService.uploadAvatar(userId, file);

        return profileService.updateAvatar(userId, url);
    }
}
