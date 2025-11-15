package com.tongji.knowpost.api.dto;

import java.util.List;

/**
 * 首页 Feed 单条记录。
 */
public record FeedItemResponse(
        String id,
        String title,
        String description,
        String coverImage,
        List<String> tags,
        String authorAvatar,
        String authorNickname,
        String tagJson,
        Long likeCount,
        Long favoriteCount,
        Boolean liked,
        Boolean faved
) {}