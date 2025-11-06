package com.tongji.knowpost.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeedCacheService {

    private final StringRedisTemplate redis;

    public void deleteAllFeedCaches() {
        Set<String> keys = redis.keys("feed:public:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public void doubleDeleteAll(long delayMillis) {
        deleteAllFeedCaches();
        try {
            Thread.sleep(Math.max(delayMillis, 50));
        } catch (InterruptedException ignored) {}
        deleteAllFeedCaches();
    }

    public void deleteMyFeedCaches(long userId) {
        Set<String> keys = redis.keys("feed:mine:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public void doubleDeleteMy(long userId, long delayMillis) {
        deleteMyFeedCaches(userId);
        try {
            Thread.sleep(Math.max(delayMillis, 50));
        } catch (InterruptedException ignored) {}
        deleteMyFeedCaches(userId);
    }
}