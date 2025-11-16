package com.tongji.relation.api;

import com.tongji.relation.service.RelationService;
import com.tongji.auth.token.JwtService;
import com.tongji.profile.api.dto.ProfileResponse;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/relation")
public class RelationController {
    private final RelationService relationService;
    private final JwtService jwtService;
    private final StringRedisTemplate redis;

    public RelationController(RelationService relationService, JwtService jwtService, StringRedisTemplate redis) {
        this.relationService = relationService;
        this.jwtService = jwtService;
        this.redis = redis;
    }

    /**
     * 发起关注。
     * @param toUserId 被关注的用户ID
     * @param jwt 认证令牌
     * @return 是否关注成功
     */
    @PostMapping("/follow")
    public boolean follow(@RequestParam("toUserId") long toUserId, @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        return relationService.follow(uid, toUserId);
    }

    /**
     * 取消关注。
     * @param toUserId 被取消关注的用户ID
     * @param jwt 认证令牌
     * @return 是否取消成功
     */
    @PostMapping("/unfollow")
    public boolean unfollow(@RequestParam("toUserId") long toUserId, @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        return relationService.unfollow(uid, toUserId);
    }

    /**
     * 查询与目标用户的关系三态。
     * @param toUserId 目标用户ID
     * @param jwt 认证令牌
     * @return following/followedBy/mutual 三态
     */
    @GetMapping("/status")
    public Map<String, Boolean> status(@RequestParam("toUserId") long toUserId, @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        return relationService.relationStatus(uid, toUserId);
    }

    /**
     * 获取关注列表，支持偏移或游标分页。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param offset 偏移量（当 cursor 为空时生效）
     * @param cursor 游标（毫秒时间戳）
     * @return 关注用户ID列表
     */
    @GetMapping("/following")
    public List<ProfileResponse> following(@RequestParam("userId") long userId,
                                @RequestParam(value = "limit", defaultValue = "20") int limit,
                                @RequestParam(value = "offset", defaultValue = "0") int offset,
                                @RequestParam(value = "cursor", required = false) Long cursor) {
        int l = Math.min(Math.max(limit, 1), 100);
        return relationService.followingProfiles(userId, l, Math.max(offset, 0), cursor);
    }

    /**
     * 获取粉丝列表，支持偏移或游标分页。
     * @param userId 用户ID
     * @param limit 返回数量上限
     * @param offset 偏移量（当 cursor 为空时生效）
     * @param cursor 游标（毫秒时间戳）
     * @return 粉丝用户ID列表
     */
    @GetMapping("/followers")
    public List<ProfileResponse> followers(@RequestParam("userId") long userId,
                                          @RequestParam(value = "limit", defaultValue = "20") int limit,
                                          @RequestParam(value = "offset", defaultValue = "0") int offset,
                                          @RequestParam(value = "cursor", required = false) Long cursor) {
        int l = Math.min(Math.max(limit, 1), 100);
        return relationService.followersProfiles(userId, l, Math.max(offset, 0), cursor);
    }

    /**
     * 获取用户维度计数（SDS）。
     * @param userId 用户ID
     * @param redis Redis 客户端
     * @return 各计数指标的值
     */
    @GetMapping("/counter")
    public Map<String, Long> counter(@RequestParam("userId") long userId) {
        byte[] raw = redis.execute((RedisCallback<byte[]>) c -> c.stringCommands().get(("ucnt:" + userId).getBytes(StandardCharsets.UTF_8)));
        Map<String, Long> m = new LinkedHashMap<>();
        if (raw == null || raw.length < 20) {
            m.put("followings", 0L);
            m.put("followers", 0L);
            m.put("posts", 0L);
            m.put("likedPosts", 0L);
            m.put("favedPosts", 0L);
            return m;
        }
        int seg = raw.length / 4;
        IntFunction<Long> read = idx -> {
            if (idx < 0 || idx >= seg) return 0L;
            int off = idx * 4;
            long n = 0;
            for (int i = 0; i < 4; i++) n = (n << 8) | (raw[off + i] & 0xFFL);
            return n;
        };
        m.put("followings", read.apply(1));
        m.put("followers", read.apply(2));
        m.put("posts", read.apply(3));
        m.put("likedPosts", read.apply(4));
        m.put("favedPosts", read.apply(5));
        return m;
    }
}
