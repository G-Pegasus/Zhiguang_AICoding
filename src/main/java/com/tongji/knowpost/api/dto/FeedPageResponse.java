package com.tongji.knowpost.api.dto;

import java.util.List;

/**
 * 首页 Feed 分页响应。
 */
public record FeedPageResponse(
        List<FeedItemResponse> items,
        int page,
        int size,
        boolean hasMore
) {}