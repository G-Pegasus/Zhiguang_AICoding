package com.tongji.relation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.MapKey;

import java.util.List;
import java.util.Map;

@Mapper
public interface RelationMapper {
    /**
     * 插入关注关系。
     * @param id 主键ID
     * @param fromUserId 发起关注的用户ID
     * @param toUserId 被关注的用户ID
     * @param relStatus 关系状态
     * @return 影响行数
     */
    int insertFollowing(@Param("id") Long id,
                        @Param("fromUserId") Long fromUserId,
                        @Param("toUserId") Long toUserId,
                        @Param("relStatus") Integer relStatus);

    /**
     * 取消关注关系（逻辑更新）。
     * @param fromUserId 发起者
     * @param toUserId 目标者
     * @return 影响行数
     */
    int cancelFollowing(@Param("fromUserId") Long fromUserId,
                        @Param("toUserId") Long toUserId);

    /**
     * 插入粉丝关系。
     * @param id 主键ID
     * @param toUserId 被关注者
     * @param fromUserId 关注者
     * @param relStatus 关系状态
     * @return 影响行数
     */
    int insertFollower(@Param("id") Long id,
                        @Param("toUserId") Long toUserId,
                        @Param("fromUserId") Long fromUserId,
                        @Param("relStatus") Integer relStatus);

    /**
     * 取消粉丝关系（逻辑更新）。
     * @param toUserId 被关注者
     * @param fromUserId 关注者
     * @return 影响行数
     */
    int cancelFollower(@Param("toUserId") Long toUserId,
                       @Param("fromUserId") Long fromUserId);

    /**
     * 判断是否存在关注关系。
     * @param fromUserId 发起者
     * @param toUserId 目标者
     * @return 是否存在（>0 表示存在）
     */
    int existsFollowing(@Param("fromUserId") Long fromUserId,
                        @Param("toUserId") Long toUserId);

    /**
     * 列出关注用户ID（偏移分页）。
     * @param fromUserId 发起者
     * @param limit 上限
     * @param offset 偏移
     * @return 关注用户ID列表
     */
    List<Long> listFollowing(@Param("fromUserId") Long fromUserId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    /**
     * 列出粉丝用户ID（偏移分页）。
     * @param toUserId 被关注者
     * @param limit 上限
     * @param offset 偏移
     * @return 粉丝用户ID列表
     */
    List<Long> listFollowers(@Param("toUserId") Long toUserId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    /**
     * 列出关注行用于缓存回填（包含 createdAt）。
     * @param fromUserId 发起者
     * @param limit 上限
     * @param offset 偏移
     * @return 以 toUserId 作为键的行映射
     */
    @MapKey("toUserId")
    Map<Long, Map<String, Object>> listFollowingRows(@Param("fromUserId") Long fromUserId,
                                                     @Param("limit") int limit,
                                                     @Param("offset") int offset);

    /**
     * 列出粉丝行用于缓存回填（包含 createdAt）。
     * @param toUserId 被关注者
     * @param limit 上限
     * @param offset 偏移
     * @return 以 fromUserId 作为键的行映射
     */
    @MapKey("fromUserId")
    Map<Long, Map<String, Object>> listFollowerRows(@Param("toUserId") Long toUserId,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);
}

