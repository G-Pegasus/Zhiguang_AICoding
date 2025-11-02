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

@RestController
@RequestMapping("/api/v1/profile")
@Validated
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final JwtService jwtService;
    private final OssStorageService ossStorageService;

    @PatchMapping
    public ProfileResponse patch(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody ProfilePatchRequest request) {
        long userId = jwtService.extractUserId(jwt);

        return profileService.updateProfile(userId, request);
    }

    @PostMapping("/avatar")
    public ProfileResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt,
                                        @RequestPart("file") MultipartFile file) {
        long userId = jwtService.extractUserId(jwt);
        String url = ossStorageService.uploadAvatar(userId, file);

        return profileService.updateAvatar(userId, url);
    }
}