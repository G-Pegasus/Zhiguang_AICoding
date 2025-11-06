package com.tongji.knowpost.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.knowpost.api.dto.KnowPostDetailResponse;
import com.tongji.storage.config.OssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowPostService {

    private final KnowPostMapper mapper;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;
    private final OssProperties ossProperties;
    private final FeedCacheService feedCacheService;

    /**
     * 创建草稿并返回新 ID。
     */
    @Transactional
    public long createDraft(long creatorId) {
        long id = idGen.nextId();
        Instant now = Instant.now();
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .status("draft")
                .type("image_text")
                .visible("public")
                .isTop(false)
                .createTime(now)
                .updateTime(now)
                .build();
        mapper.insertDraft(post);
        return id;
    }

    /**
     * 确认内容上传（写入 objectKey、etag、大小、校验和，并生成公共 URL）。
     */
    @Transactional
    public void confirmContent(long creatorId, long id, String objectKey, String etag, Long size, String sha256) {
        // 缓存双删（更新前先删除）
        feedCacheService.deleteAllFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .contentObjectKey(objectKey)
                .contentEtag(etag)
                .contentSize(size)
                .contentSha256(sha256)
                .contentUrl(publicUrl(objectKey))
                .updateTime(Instant.now())
                .build();
        int updated = mapper.updateContent(post);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        // 更新后再次删除，避免并发下写回旧值
        feedCacheService.doubleDeleteAll(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
    }

    /**
     * 更新元数据：标题、标签、可见性、置顶、图片列表等。
     */
    @Transactional
    public void updateMetadata(long creatorId, long id, String title, Long tagId, List<String> tags, List<String> imgUrls, String visible, Boolean isTop, String description) {
        // 缓存双删（更新前先删除）
        feedCacheService.deleteAllFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        KnowPost post = KnowPost.builder()
                .id(id)
                .creatorId(creatorId)
                .title(title)
                .tagId(tagId)
                .tags(toJsonOrNull(tags))
                .imgUrls(toJsonOrNull(imgUrls))
                .visible(visible)
                .isTop(isTop)
                .description(description)
                .type("image_text")
                .updateTime(Instant.now())
                .build();

        int updated = mapper.updateMetadata(post);

        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        // 更新后再次删除，避免并发下写回旧值
        feedCacheService.doubleDeleteAll(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
    }

    /**
     * 发布草稿，设置状态与发布时间。
     */
    @Transactional
    public void publish(long creatorId, long id) {
        // 缓存双删（更新前先删除）
        feedCacheService.deleteAllFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        int updated = mapper.publish(id, creatorId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        // 更新后再次删除，避免并发下写回旧值
        feedCacheService.doubleDeleteAll(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
    }

    /**
     * 设置置顶。
     */
    @Transactional
    public void updateTop(long creatorId, long id, boolean isTop) {
        feedCacheService.deleteAllFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        int updated = mapper.updateTop(id, creatorId, isTop);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        feedCacheService.doubleDeleteAll(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
    }

    /**
     * 设置可见性（权限）。
     */
    @Transactional
    public void updateVisibility(long creatorId, long id, String visible) {
        if (!isValidVisible(visible)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "可见性取值非法");
        }
        feedCacheService.deleteAllFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        int updated = mapper.updateVisibility(id, creatorId, visible);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        feedCacheService.doubleDeleteAll(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
    }

    /**
     * 软删除。
     */
    @Transactional
    public void delete(long creatorId, long id) {
        feedCacheService.deleteAllFeedCaches();
        feedCacheService.deleteMyFeedCaches(creatorId);
        int updated = mapper.softDelete(id, creatorId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        feedCacheService.doubleDeleteAll(200);
        feedCacheService.doubleDeleteMy(creatorId, 200);
    }

    private boolean isValidVisible(String visible) {
        if (visible == null) return false;
        return switch (visible) {
            case "public", "followers", "school", "private", "unlisted" -> true;
            default -> false;
        };
    }

    private String toJsonOrNull(List<String> list) {
        if (list == null) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON 处理失败");
        }
    }

    private String publicUrl(String objectKey) {
        String publicDomain = ossProperties.getPublicDomain();
        if (publicDomain != null && !publicDomain.isBlank()) {
            return publicDomain.replaceAll("/$", "") + "/" + objectKey;
        }
        return "https://" + ossProperties.getBucket() + "." + ossProperties.getEndpoint() + "/" + objectKey;
    }

    /**
     * 获取知文详情（含作者信息、图片列表）。
     * - 公开策略：published + public 可匿名访问；否则需作者本人访问。
     * - 软删除内容不可见。
     */
    @Transactional(readOnly = true)
    public KnowPostDetailResponse getDetail(long id, Long currentUserIdNullable) {
        KnowPostDetailRow row = mapper.findDetailById(id);
        if (row == null || "deleted".equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不存在");
        }

        boolean isPublic = "published".equals(row.getStatus()) && "public".equals(row.getVisible());
        boolean isOwner = currentUserIdNullable != null && row.getCreatorId() != null && currentUserIdNullable.equals(row.getCreatorId());
        if (!isPublic && !isOwner) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无权限查看");
        }

        List<String> images = parseStringArray(row.getImgUrls());
        List<String> tags = parseStringArray(row.getTags());

        return new KnowPostDetailResponse(
                String.valueOf(row.getId()),
                row.getTitle(),
                row.getDescription(),
                row.getContentUrl(),
                images,
                tags,
                row.getAuthorAvatar(),
                row.getAuthorNickname(),
                row.getAuthorTagJson(),
                0L,
                0L,
                row.getIsTop(),
                row.getVisible(),
                row.getType(),
                row.getPublishTime()
        );
    }

    private java.util.List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) return java.util.Collections.emptyList();
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }
}