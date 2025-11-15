package com.tongji.relation.processor;

import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.tongji.counter.service.UserCounterService;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class RelationEventProcessor {
    private final RelationMapper mapper;
    private final StringRedisTemplate redis;
    private final UserCounterService userCounterService;

    public RelationEventProcessor(RelationMapper mapper, StringRedisTemplate redis, UserCounterService userCounterService) {
        this.mapper = mapper;
        this.redis = redis;
        this.userCounterService = userCounterService;
    }

    /**
     * 处理关系事件：入库、更新缓存、刷新计数，并进行幂等去重。
     * @param evt 关系事件
     */
    public void process(RelationEvent evt) {
        String dk = "dedup:rel:" + evt.getType() + ":" + evt.getFromUserId() + ":" + evt.getToUserId() + ":" + (evt.getId() == null ? "0" : String.valueOf(evt.getId()));
        Boolean first = redis.opsForValue().setIfAbsent(dk, "1", Duration.ofMinutes(10));

        if (first == null || !first) {
            return;
        }
        if ("FollowCreated".equals(evt.getType())) {
            // 异步插入粉丝表
            mapper.insertFollower(evt.getId(), evt.getToUserId(), evt.getFromUserId(), 1);
            long now = System.currentTimeMillis();
            redis.opsForZSet().add("uf:flws:" + evt.getFromUserId(), String.valueOf(evt.getToUserId()), now);
            redis.opsForZSet().add("uf:fans:" + evt.getToUserId(), String.valueOf(evt.getFromUserId()), now);
            redis.expire("uf:flws:" + evt.getFromUserId(), Duration.ofHours(2));
            redis.expire("uf:fans:" + evt.getToUserId(), Duration.ofHours(2));
            userCounterService.incrementFollowings(evt.getFromUserId(), 1);
            userCounterService.incrementFollowers(evt.getToUserId(), 1);
        } else if ("FollowCanceled".equals(evt.getType())) {
            mapper.cancelFollower(evt.getToUserId(), evt.getFromUserId());
            redis.opsForZSet().remove("uf:flws:" + evt.getFromUserId(), String.valueOf(evt.getToUserId()));
            redis.opsForZSet().remove("uf:fans:" + evt.getToUserId(), String.valueOf(evt.getFromUserId()));
            redis.expire("uf:flws:" + evt.getFromUserId(), Duration.ofHours(2));
            redis.expire("uf:fans:" + evt.getToUserId(), Duration.ofHours(2));
            userCounterService.incrementFollowings(evt.getFromUserId(), -1);
            userCounterService.incrementFollowers(evt.getToUserId(), -1);
        }
    }
}
