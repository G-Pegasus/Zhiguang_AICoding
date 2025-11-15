package com.tongji.knowpost.service;

import com.tongji.knowpost.api.dto.KnowPostDetailResponse;

import java.util.List;

/**
 * 知文业务接口。
 */
public interface KnowPostService {

    long createDraft(long creatorId);

    void confirmContent(long creatorId, long id, String objectKey, String etag, Long size, String sha256);

    void updateMetadata(long creatorId, long id, String title, Long tagId, List<String> tags, List<String> imgUrls, String visible, Boolean isTop, String description);

    void publish(long creatorId, long id);

    void updateTop(long creatorId, long id, boolean isTop);

    void updateVisibility(long creatorId, long id, String visible);

    void delete(long creatorId, long id);

    KnowPostDetailResponse getDetail(long id, Long currentUserIdNullable);
}