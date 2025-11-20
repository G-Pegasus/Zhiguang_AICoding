package com.tongji.counter.service;

/**
 * 用户维度计数服务接口。
 *
 * <p>支持维护关注数、粉丝数、发文数、获赞数、获收藏数，并提供全量重建。</p>
 */
public interface UserCounterService {
    /** 增量更新关注数 */
    void incrementFollowings(long userId, int delta);
    /** 增量更新粉丝数 */
    void incrementFollowers(long userId, int delta);
    /** 增量更新发文数 */
    void incrementPosts(long userId, int delta);
    /** 增量更新获赞数（作者维度） */
    void incrementLikesReceived(long userId, int delta);
    /** 增量更新获收藏数（作者维度） */
    void incrementFavsReceived(long userId, int delta);
    /** 基于事实重建全部计数 */
    void rebuildAllCounters(long userId);
}

