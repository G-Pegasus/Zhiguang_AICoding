package com.tongji.search.api.dto;

import com.tongji.knowpost.api.dto.FeedItemResponse;
import java.util.List;

/**
 * 搜索响应：包含结果列表与分页游标。
 */
public record SearchResponse(
        List<FeedItemResponse> items,
        String nextAfter,
        boolean hasMore
) {}