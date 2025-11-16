package com.tongji.relation.service.impl;

import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.service.RelationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.outbox.OutboxMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tongji.user.mapper.UserMapper;
import com.tongji.user.domain.User;
import com.tongji.profile.api.dto.ProfileResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.core.RedisCallback;

@Service
public class RelationServiceImpl implements RelationService {
    private final RelationMapper mapper;
    private final OutboxMapper outboxMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> tokenScript;
    private final ObjectMapper objectMapper;
    private final Cache<Long, List<Long>> flwsTopCache;
    private final Cache<Long, List<Long>> fansTopCache;
    private final UserMapper userMapper;
    

    /**
     * 关系服务实现构造函数。
     * @param mapper 关系表数据访问
     * @param outboxMapper Outbox 事件写入访问
     * @param redis Redis 客户端
     * @param objectMapper JSON 序列化器
     */
    public RelationServiceImpl(RelationMapper mapper,
                               OutboxMapper outboxMapper,
                               StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               UserMapper userMapper) {
        this.mapper = mapper;
        this.outboxMapper = outboxMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.tokenScript = new DefaultRedisScript<>();
        this.tokenScript.setResultType(Long.class);
        this.tokenScript.setScriptText(TOKEN_BUCKET_LUA);
        this.flwsTopCache = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(10)).build();
        this.fansTopCache = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(10)).build();
        this.userMapper = userMapper;
    }

    /**
     * 关注操作，限流通过令牌桶，并写入 Outbox 以异步构建缓存与粉丝表。
     * @param fromUserId 发起关注的用户ID
     * @param toUserId 被关注的用户ID
     * @return 是否关注成功
     */
    @Override
    @Transactional
    public boolean follow(long fromUserId, long toUserId) {
        // Lua 脚本令牌桶限流
        Long ok = redis.execute(tokenScript, List.of("rl:follow:" + fromUserId), "100", "1");
        if (ok == null || ok == 0L) {
            return false;
        }

        long id = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        int inserted = mapper.insertFollowing(id, fromUserId, toUserId, 1);

        if (inserted > 0) {
            try {
                Long outId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
                String payload = objectMapper.writeValueAsString(new RelationEvent("FollowCreated", fromUserId, toUserId, id));
                outboxMapper.insert(outId, "following", id, "FollowCreated", payload);
            } catch (Exception ignored) {}

            return true;
        }
        return false;
    }

    /**
     * 取消关注操作，并写入 Outbox 事件。
     * @param fromUserId 发起取消关注的用户ID
     * @param toUserId 被取消关注的用户ID
     * @return 是否取消成功
     */
    @Override
    @Transactional
    public boolean unfollow(long fromUserId, long toUserId) {
        int updated = mapper.cancelFollowing(fromUserId, toUserId);
        if (updated > 0) {
            try {
                Long outId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
                String payload = objectMapper.writeValueAsString(new RelationEvent("FollowCanceled", fromUserId, toUserId, null));
                outboxMapper.insert(outId, "following", null, "FollowCanceled", payload);
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    /**
     * 判断是否已关注。
     * @param fromUserId 关注发起者
     * @param toUserId 被关注者
     * @return 是否已关注
     */
    @Override
    public boolean isFollowing(long fromUserId, long toUserId) {
        return mapper.existsFollowing(fromUserId, toUserId) > 0;
    }

    /**
     * 获取关注列表（偏移分页），优先读取 Redis ZSet，未命中时回填并设置 TTL。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param offset 偏移量
     * @return 关注的用户ID列表
     */
    @Override
    public List<Long> following(long userId, int limit, int offset) {
        String key = "uf:flws:" + userId;
        return getListWithOffset(
                key,
                offset,
                limit,
                need -> mapper.listFollowingRows(userId, need, 0),
                "toUserId",
                "createdAt",
                flwsTopCache,
                userId
        );
    }

    /**
     * 获取粉丝列表（偏移分页），ZSet 优先，DB 回填并设置 TTL。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param offset 偏移量
     * @return 粉丝用户ID列表
     */
    @Override
    public List<Long> followers(long userId, int limit, int offset) {
        String key = "uf:fans:" + userId;
        return getListWithOffset(
                key,
                offset,
                limit,
                need -> mapper.listFollowerRows(userId, need, 0),
                "fromUserId",
                "createdAt",
                fansTopCache,
                userId
        );
    }

    /**
     * 查询双方关系状态。
     * @param userId 当前用户ID
     * @param otherUserId 对方用户ID
     * @return 三态关系：following/followedBy/mutual
     */
    @Override
    public Map<String, Boolean> relationStatus(long userId, long otherUserId) {
        boolean following = isFollowing(userId, otherUserId);
        boolean followedBy = isFollowing(otherUserId, userId);
        boolean mutual = following && followedBy;
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("following", following);
        m.put("followedBy", followedBy);
        m.put("mutual", mutual);
        return m;
    }

    /**
     * 游标分页获取关注列表，按创建时间倒序基于 ZSet 分数。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param cursor 上一页末条的分数（毫秒时间戳），为空代表第一页
     * @return 关注的用户ID列表
     */
    @Override
    public List<Long> followingCursor(long userId, int limit, Long cursor) {
        String key = "uf:flws:" + userId;
        return getListWithCursor(
                key,
                limit,
                cursor,
                need -> mapper.listFollowingRows(userId, need, 0),
                "toUserId",
                "createdAt"
        );
    }

    /**
     * 游标分页获取粉丝列表。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param cursor 上一页末条的分数（毫秒时间戳），为空代表第一页
     * @return 粉丝用户ID列表
     */
    @Override
    public List<Long> followersCursor(long userId, int limit, Long cursor) {
        String key = "uf:fans:" + userId;
        return getListWithCursor(
                key,
                limit,
                cursor,
                need -> mapper.listFollowerRows(userId, need, 0),
                "fromUserId",
                "createdAt"
        );
    }

    @Override
    public List<ProfileResponse> followingProfiles(long userId, int limit, int offset, Long cursor) {
        List<Long> ids = cursor != null ? followingCursor(userId, limit, cursor)
                                        : following(userId, limit, offset);
        return toProfiles(ids);
    }

    @Override
    public List<ProfileResponse> followersProfiles(long userId, int limit, int offset, Long cursor) {
        List<Long> ids = cursor != null ? followersCursor(userId, limit, cursor)
                                        : followers(userId, limit, offset);
        return toProfiles(ids);
    }

    private List<ProfileResponse> toProfiles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<User> users = userMapper.listByIds(ids);
        Map<Long, User> m = new LinkedHashMap<>(users.size());
        for (User u : users) m.put(u.getId(), u);
        List<ProfileResponse> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            User u = m.get(id);
            if (u == null) continue;
            out.add(new ProfileResponse(u.getId(), u.getNickname(), u.getAvatar(), u.getBio(), u.getZgId(), u.getGender(), u.getBirthday(), u.getSchool(), u.getPhone(), u.getEmail(), u.getTagsJson()));
        }
        return out;
    }

    /**
     * 判断是否为大V（基于 followers 计数阈值）。
     * @param userId 用户ID
     * @return 是否为大V
     */
    private boolean isBigV(long userId) {
        byte[] raw = redis.execute((RedisCallback<byte[]>) c -> c.stringCommands().get(("ucnt:" + userId).getBytes(StandardCharsets.UTF_8)));
        if (raw == null || raw.length < 20) return false;
        long n = 0;
        int off = 2 * 4;
        for (int i = 0; i < 4; i++) n = (n << 8) | (raw[off + i] & 0xFFL);
        return n >= 500_000L;
    }

    private List<Long> getListWithOffset(
            String key,
            int offset,
            int limit,
            IntFunction<Map<Long, Map<String, Object>>> rowsFetcher,
            String idField,
            String tsField,
            Cache<Long, List<Long>> localCache,
            long userId
    ) {
        Set<String> cached = redis.opsForZSet().reverseRange(key, offset, offset + limit - 1L);
        if (cached != null && !cached.isEmpty()) {
            return toLongList(cached);
        }

        List<Long> top = localCache != null ? localCache.getIfPresent(userId) : null;
        if (top != null && !top.isEmpty()) {
            int from = Math.min(offset, top.size());
            int to = Math.min(offset + limit, top.size());
            return new ArrayList<>(top.subList(from, to));
        }

        int need = Math.max(1, limit + offset);
        Map<Long, Map<String, Object>> rows = rowsFetcher.apply(Math.min(need, 1000));
        if (rows != null && !rows.isEmpty()) {
            fillZSet(key, rows, idField, tsField, null);
            redis.expire(key, Duration.ofHours(2));
            if (localCache != null && isBigV(userId)) {
                maybeUpdateTopCache(userId, key, localCache);
            }
            Set<String> filled = redis.opsForZSet().reverseRange(key, offset, offset + limit - 1L);
            return filled == null ? Collections.emptyList() : toLongList(filled);
        }
        return Collections.emptyList();
    }

    private List<Long> getListWithCursor(String key,
                                         int limit,
                                         Long cursor,
                                         IntFunction<Map<Long, Map<String, Object>>> rowsFetcher,
                                         String idField,
                                         String tsField) {
        double max = cursor == null ? Double.POSITIVE_INFINITY : cursor.doubleValue();
        Set<String> cached = redis.opsForZSet().reverseRangeByScore(key, max, Double.NEGATIVE_INFINITY, 0, limit);
        if (cached != null && !cached.isEmpty()) {
            return toLongList(cached);
        }
        int need = Math.max(limit, 100);
        Map<Long, Map<String, Object>> rows = rowsFetcher.apply(Math.min(need, 1000));
        if (rows != null && !rows.isEmpty()) {
            fillZSet(key, rows, idField, tsField, cursor);
            redis.expire(key, Duration.ofHours(2));
            Set<String> filled = redis.opsForZSet().reverseRangeByScore(key, max, Double.NEGATIVE_INFINITY, 0, limit);
            return filled == null ? Collections.emptyList() : toLongList(filled);
        }
        return Collections.emptyList();
    }

    private void fillZSet(String key,
                          Map<Long, Map<String, Object>> rows,
                          String idField,
                          String tsField,
                          Long cursor) {
        for (Map<String, Object> r : rows.values()) {
            Object idObj = r.get(idField);
            Object tsObj = r.get(tsField);
            if (idObj == null || tsObj == null) continue;
            long score = tsScore(tsObj);
            if (cursor == null || score <= cursor) {
                redis.opsForZSet().add(key, String.valueOf(idObj), score);
            }
        }
    }

    private long tsScore(Object tsObj) {
        if (tsObj instanceof Timestamp ts) return ts.getTime();
        if (tsObj instanceof Date d) return d.getTime();
        return System.currentTimeMillis();
    }

    private List<Long> toLongList(Set<String> set) {
        List<Long> out = new ArrayList<>(set.size());
        for (String s : set) out.add(Long.valueOf(s));
        return out;
    }

    private void maybeUpdateTopCache(long userId, String key, Cache<Long, List<Long>> cache) {
        Set<String> allSet = redis.opsForZSet().reverseRange(key, 0, 499);
        if (allSet == null || allSet.isEmpty()) return;
        List<Long> all = new ArrayList<>(allSet.size());
        for (String s : allSet) all.add(Long.valueOf(s));
        cache.put(userId, all);
    }

    private static final String TOKEN_BUCKET_LUA = "\n" +
            "local key = KEYS[1]\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local rate = tonumber(ARGV[2])\n" +
            "local now = redis.call('TIME')[1]\n" +
            "local last = redis.call('HGET', key, 'last')\n" +
            "local tokens = redis.call('HGET', key, 'tokens')\n" +
            "if not last then last = now; tokens = capacity end\n" +
            "local elapsed = tonumber(now) - tonumber(last)\n" +
            "local add = elapsed * rate\n" +
            "tokens = math.min(capacity, tonumber(tokens) + add)\n" +
            "if tokens < 1 then redis.call('HSET', key, 'last', now); redis.call('HSET', key, 'tokens', tokens); return 0 end\n" +
            "tokens = tokens - 1\n" +
            "redis.call('HSET', key, 'last', now)\n" +
            "redis.call('HSET', key, 'tokens', tokens)\n" +
            "redis.call('PEXPIRE', key, 60000)\n" +
            "return 1\n";
}
