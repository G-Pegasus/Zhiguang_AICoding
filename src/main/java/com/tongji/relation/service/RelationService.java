package com.tongji.relation.service;

import java.util.List;
import java.util.Map;

public interface RelationService {
    /**
     * 关注操作。
     * @param fromUserId 发起关注的用户ID
     * @param toUserId 被关注的用户ID
     * @return 是否关注成功
     */
    boolean follow(long fromUserId, long toUserId);
    /**
     * 取消关注。
     * @param fromUserId 发起取消的用户ID
     * @param toUserId 被取消的用户ID
     * @return 是否取消成功
     */
    boolean unfollow(long fromUserId, long toUserId);
    /**
     * 判断是否已关注。
     * @param fromUserId 关注发起者
     * @param toUserId 被关注者
     * @return 是否已关注
     */
    boolean isFollowing(long fromUserId, long toUserId);
    /**
     * 获取关注列表（偏移分页）。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param offset 偏移量
     * @return 关注用户ID列表
     */
    List<Long> following(long userId, int limit, int offset);
    /**
     * 获取粉丝列表（偏移分页）。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param offset 偏移量
     * @return 粉丝用户ID列表
     */
    List<Long> followers(long userId, int limit, int offset);
    /**
     * 查询双方关系三态。
     * @param userId 当前用户ID
     * @param otherUserId 对方用户ID
     * @return following/followedBy/mutual 三态
     */
    Map<String, Boolean> relationStatus(long userId, long otherUserId);
    /**
     * 游标分页关注列表。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param cursor 上一页末条的分数（毫秒时间戳），为空代表第一页
     * @return 关注用户ID列表
     */
    List<Long> followingCursor(long userId, int limit, Long cursor);
    /**
     * 游标分页粉丝列表。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param cursor 上一页末条的分数（毫秒时间戳），为空代表第一页
     * @return 粉丝用户ID列表
     */
    List<Long> followersCursor(long userId, int limit, Long cursor);
}

