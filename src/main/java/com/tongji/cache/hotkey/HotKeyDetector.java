package com.tongji.cache.hotkey;

import com.tongji.cache.config.CacheProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HotKeyDetector {
    public enum Level { NONE, LOW, MEDIUM, HIGH }

    private final CacheProperties properties;
    private final Map<String, int[]> counters = new ConcurrentHashMap<>();
    private final AtomicInteger current = new AtomicInteger(0);
    private final int segments;

    public HotKeyDetector(CacheProperties properties) {
        this.properties = properties;
        int segSeconds = properties.getHotkey().getSegmentSeconds();
        int winSeconds = properties.getHotkey().getWindowSeconds();
        this.segments = Math.max(1, winSeconds / Math.max(1, segSeconds));
    }

    public void record(String key) {
        int[] arr = counters.computeIfAbsent(key, k -> new int[segments]);
        arr[current.get()]++;
    }

    public int heat(String key) {
        int[] arr = counters.get(key);
        if (arr == null) return 0;
        int sum = 0;
        for (int v : arr) sum += v;
        return sum;
    }

    public Level level(String key) {
        int h = heat(key);
        if (h >= properties.getHotkey().getLevelHigh()) return Level.HIGH;
        if (h >= properties.getHotkey().getLevelMedium()) return Level.MEDIUM;
        if (h >= properties.getHotkey().getLevelLow()) return Level.LOW;
        return Level.NONE;
    }

    public int ttlForPublic(int baseTtlSeconds, String key) {
        Level l = level(key);
        return baseTtlSeconds + extendSeconds(l);
    }

    public int ttlForMine(int baseTtlSeconds, String key) {
        Level l = level(key);
        return baseTtlSeconds + extendSeconds(l);
    }

    private int extendSeconds(Level l) {
        return switch (l) {
            case HIGH -> properties.getHotkey().getExtendHighSeconds();
            case MEDIUM -> properties.getHotkey().getExtendMediumSeconds();
            case LOW -> properties.getHotkey().getExtendLowSeconds();
            default -> 0;
        };
    }

    @Scheduled(fixedRateString = "${cache.hotkey.segment-seconds:10}000")
    public void rotate() {
        int next = (current.get() + 1) % segments;
        current.set(next);
        for (int[] arr : counters.values()) {
            arr[next] = 0;
        }
    }

    public void reset(String key) {
        int[] arr = counters.get(key);
        if (arr != null) Arrays.fill(arr, 0);
    }
}

