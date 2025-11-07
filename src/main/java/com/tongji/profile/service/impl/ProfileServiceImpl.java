package com.tongji.profile.service.impl;

import com.tongji.profile.api.dto.ProfilePatchRequest;
import com.tongji.profile.api.dto.ProfileResponse;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import com.tongji.user.domain.User;
import com.tongji.user.mapper.UserMapper;
import com.tongji.profile.service.ProfileService;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<User> getById(long userId) {
        return Optional.ofNullable(userMapper.findById(userId));
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(long userId, ProfilePatchRequest req) {
        User current = userMapper.findById(userId);

        if (current == null) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "用户不存在");
        }

        boolean hasAnyField = req.nickname() != null || req.bio() != null || req.gender() != null
                || req.birthday() != null || req.zgId() != null || req.school() != null
                || req.tagJson() != null;

        if (!hasAnyField) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未提交任何更新字段");
        }

        if (req.zgId() != null && !req.zgId().isBlank()) {
            boolean exists = userMapper.existsByZgIdExceptId(req.zgId(), current.getId());

            if (exists) {
                throw new BusinessException(ErrorCode.ZGID_EXISTS);
            }
        }

        User patch = getUser(req, current);
        userMapper.updateProfile(patch);
        User updated = userMapper.findById(userId);

        return toResponse(updated);
    }

    private static User getUser(ProfilePatchRequest req, User current) {
        User patch = new User();
        patch.setId(current.getId());
        if (req.nickname() != null) {
            patch.setNickname(req.nickname().trim());
        }
        if (req.bio() != null) {
            patch.setBio(req.bio().trim());
        }
        if (req.gender() != null) {
            patch.setGender(req.gender().trim().toUpperCase());
        }
        if (req.birthday() != null) {
            patch.setBirthday(req.birthday());
        }
        if (req.zgId() != null) {
            patch.setZgId(req.zgId().trim());
        }
        if (req.school() != null) {
            patch.setSchool(req.school().trim());
        }
        if (req.tagJson() != null) {
            patch.setTagsJson(req.tagJson());
        }
        return patch;
    }

    @Override
    @Transactional
    public ProfileResponse updateAvatar(long userId, String avatarUrl) {
        User current = userMapper.findById(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "用户不存在");
        }

        User patch = new User();
        patch.setId(userId);
        patch.setAvatar(avatarUrl);
        userMapper.updateProfile(patch);

        User updated = userMapper.findById(userId);
        return toResponse(updated);
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getBio(),
                user.getZgId(),
                user.getGender(),
                user.getBirthday(),
                user.getSchool(),
                user.getPhone(),
                user.getEmail(),
                user.getTagsJson()
        );
    }
}