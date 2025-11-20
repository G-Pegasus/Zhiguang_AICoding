package com.tongji.knowpost.mapper;

import com.tongji.knowpost.model.KnowPost;
import com.tongji.knowpost.model.KnowPostDetailRow;

import com.tongji.knowpost.model.KnowPostFeedRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowPostMapper {
    void insertDraft(KnowPost post);

    KnowPost findById(@Param("id") Long id);

    int updateContent(KnowPost post);

    int updateMetadata(KnowPost post);

    int publish(@Param("id") Long id, @Param("creatorId") Long creatorId);

    // 首页 Feed 列表（已发布、公开可见），置顶优先，其次按发布时间倒序。
    List<KnowPostFeedRow> listFeedPublic(@Param("limit") int limit,
                                         @Param("offset") int offset);

    // 我的知文列表（当前用户已发布内容），置顶优先，其次按发布时间倒序。
    List<KnowPostFeedRow> listMyPublished(@Param("creatorId") long creatorId,
                                                                              @Param("limit") int limit,
                                                                              @Param("offset") int offset);

    // 设置置顶
    int updateTop(@Param("id") Long id, @Param("creatorId") Long creatorId, @Param("isTop") Boolean isTop);

    // 设置可见性
    int updateVisibility(@Param("id") Long id, @Param("creatorId") Long creatorId, @Param("visible") String visible);

    // 软删除
    int softDelete(@Param("id") Long id, @Param("creatorId") Long creatorId);

    // 详情查询（含作者信息）
    KnowPostDetailRow findDetailById(@Param("id") Long id);

    // 统计我的已发布知文数量
    long countMyPublished(@Param("creatorId") long creatorId);

    // 列出我的已发布知文ID列表
    List<Long> listMyPublishedIds(@Param("creatorId") long creatorId);
}