package com.tongji.cache.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {
    @Bean("feedPublicCache")
    public Cache<String, FeedPageResponse> feedPublicCache(CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getL2().getPublicCfg().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(props.getL2().getPublicCfg().getTtlSeconds()))
                .build();
    }

    @Bean("feedMineCache")
    public Cache<String, FeedPageResponse> feedMineCache(CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getL2().getMineCfg().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(props.getL2().getMineCfg().getTtlSeconds()))
                .build();
    }
}

