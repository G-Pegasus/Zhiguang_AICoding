package com.tongji.profile.service.impl;

import com.tongji.profile.api.dto.ProfilePatchRequest;
import com.tongji.profile.api.dto.ProfileResponse;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import com.tongji.user.domain.User;
import com.tongji.user.mapper.UserMapper;
import com.tongji.profile.service.ProfileService;

/**
 * 个人资料服务实现。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>读取用户资料</li>
 *   <li>校验并更新用户基础信息（昵称/简介/性别/生日/学校/标签等）</li>
 *   <li>更新头像 URL</li>
 * </ul>
 *
 * <p>错误处理：通过抛出 {@link BusinessException} 携带 {@link ErrorCode}，由全局异常处理器统一返回 HTTP 400。</p>
 */
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;

    /**
     * 按用户 ID 查询用户实体。
     *
     * <p>只读事务用于减少不必要的写锁与脏检查。</p>
     *
     * @param userId 用户 ID
     * @return 用户实体（不存在则为 {@link Optional#empty()}）
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<User> getById(long userId) {
        return Optional.ofNullable(userMapper.findById(userId));
    }

    /**
     * 更新个人资料（支持部分字段更新）。
     *
     * <p>更新流程：</p>
     * <ul>
     *   <li>校验用户存在</li>
     *   <li>校验至少提供一个待更新字段</li>
     *   <li>若提交知光号（zgId），校验唯一性</li>
     *   <li>构造 patch 对象并执行更新</li>
     *   <li>重新查询并返回更新后的快照</li>
     * </ul>
     *
     * @param userId 当前登录用户 ID
     * @param req patch 请求（字段可空，非空字段会被更新）
     * @return 更新后的个人资料响应
     */
    @Override
    @Transactional
    public ProfileResponse updateProfile(long userId, ProfilePatchRequest req) {
        // 读取当前用户，作为更新与唯一性校验的基准
        User current = userMapper.findById(userId);

        if (current == null) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "用户不存在");
        }

        // 至少要提交一个字段，否则属于无效请求
        boolean hasAnyField = req.nickname() != null || req.bio() != null || req.gender() != null
                || req.birthday() != null || req.zgId() != null || req.school() != null
                || req.tagJson() != null;

        if (!hasAnyField) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未提交任何更新字段");
        }

        // 知光号唯一性校验：仅在提交且非空时检查（排除自己）
        if (req.zgId() != null && !req.zgId().isBlank()) {
            boolean exists = userMapper.existsByZgIdExceptId(req.zgId(), current.getId());

            if (exists) {
                throw new BusinessException(ErrorCode.ZGID_EXISTS);
            }
        }

        // 仅写入非空字段，避免把未提交字段覆盖成 null
        User patch = getUser(req, current);
        userMapper.updateProfile(patch);

        // 更新后回读，保证返回数据为最新快照
        User updated = userMapper.findById(userId);

        return toResponse(updated);
    }

    /**
     * 将 patch 请求转换为用户更新对象。
     *
     * <p>仅对非空字段进行 set，且对字符串做 trim/归一化处理。</p>
     */
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

    /**
     * 更新用户头像 URL。
     *
     * <p>头像文件上传由上层完成，此处只负责将 URL 写入用户资料。</p>
     *
     * @param userId 当前登录用户 ID
     * @param avatarUrl 头像 URL（通常来自对象存储上传返回）
     * @return 更新后的个人资料响应
     */
    @Override
    @Transactional
    public ProfileResponse updateAvatar(long userId, String avatarUrl) {
        User current = userMapper.findById(userId);
        if (current == null) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "用户不存在");
        }

        // 仅更新头像字段
        User patch = new User();
        patch.setId(userId);
        patch.setAvatar(avatarUrl);
        userMapper.updateProfile(patch);

        // 更新后回读，保证返回最新头像地址
        User updated = userMapper.findById(userId);
        return toResponse(updated);
    }

    /**
     * 将用户实体映射为对外响应 DTO。
     */
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
