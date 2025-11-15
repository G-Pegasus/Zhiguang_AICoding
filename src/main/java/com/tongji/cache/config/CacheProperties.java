package com.tongji.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cache")
@Data
public class CacheProperties {
    private L2 l2 = new L2();
    private Hotkey hotkey = new Hotkey();

    @Data
    public static class L2 {
        private PublicCfg publicCfg = new PublicCfg();
        private MineCfg mineCfg = new MineCfg();
    }

    @Data
    public static class PublicCfg {
        private int ttlSeconds = 15;
        private long maxSize = 1000;
    }

    @Data
    public static class MineCfg {
        private int ttlSeconds = 10;
        private long maxSize = 1000;
    }

    @Data
    public static class Hotkey {
        private int windowSeconds = 60;
        private int segmentSeconds = 10;
        private int levelLow = 50;
        private int levelMedium = 200;
        private int levelHigh = 500;
        private int extendLowSeconds = 20;
        private int extendMediumSeconds = 60;
        private int extendHighSeconds = 120;
    }
}

