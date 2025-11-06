package com.tongji.knowpost.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 知文详情响应。
 */
public record KnowPostDetailResponse(
        String id,
        String title,
        String description,
        String contentUrl,
        List<String> images,
        List<String> tags,
        String authorAvatar,
        String authorNickname,
        String authorTagJson,
        Long likeCount,
        Long favoriteCount,
        Boolean isTop,
        String visible,
        String type,
        Instant publishTime
) {}