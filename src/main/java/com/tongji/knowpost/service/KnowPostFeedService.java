package com.tongji.knowpost.service;

import com.tongji.knowpost.api.dto.FeedPageResponse;

/**
 * 知文 Feed 业务接口。
 */
public interface KnowPostFeedService {
    FeedPageResponse getPublicFeed(int page, int size, Long currentUserIdNullable);

    FeedPageResponse getMyPublished(long userId, int page, int size);
}