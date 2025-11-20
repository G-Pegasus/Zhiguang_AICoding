package com.tongji.cache.hotkey;

import com.tongji.cache.config.CacheProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 热键探测器（滑动时间窗口计数 + 热度分级 + TTL 动态扩展）。
 * <p>
 * 设计说明：
 * - 采用固定分段滑动窗口：窗口长度 windowSeconds，分段长度 segmentSeconds，段数 segments=window/segment；
 * - 每个 key 维护长度为 segments 的数组 counters[key]，current 指向当前活跃段；
 * - 周期性 rotate 将 current 前移并清零新段，实现近窗口热度的自然衰减；
 * - 根据总热度 h=Σ段计数，映射到 NONE/LOW/MEDIUM/HIGH 的热度等级；
 * - 提供 ttlForPublic/ttlForMine：在基准 TTL 上叠加等级扩展秒数，保护热点请求。
 * <p>
 * 并发语义：
 * - 使用 ConcurrentHashMap 存储计数数组，AtomicInteger 维护段游标；
 * - 计数递增为无锁数组操作，rotate 仅清零新段，避免大范围写冲突；
 * - 统计为近似滑窗，保证在高并发下的稳定与低开销。
 */
@Component
public class HotKeyDetector {
    public enum Level { NONE, LOW, MEDIUM, HIGH }

    /** 缓存配置（包含窗口/分段参数、等级阈值、扩展秒数） */
    private final CacheProperties properties;
    /** 每个 key 的滑窗分段计数数组，长度为 segments */
    private final Map<String, int[]> counters = new ConcurrentHashMap<>();
    /** 当前活跃分段索引（原子维护） */
    private final AtomicInteger current = new AtomicInteger(0);
    /** 滑窗分段数量：windowSeconds / segmentSeconds */
    private final int segments;

    /**
     * 初始化探测器：根据配置计算分段数量。
     * @param properties 缓存配置（hotkey）
     */
    public HotKeyDetector(CacheProperties properties) {
        this.properties = properties;
        int segSeconds = properties.getHotkey().getSegmentSeconds();
        int winSeconds = properties.getHotkey().getWindowSeconds();
        this.segments = Math.max(1, winSeconds / Math.max(1, segSeconds));
    }

    /**
     * 记录一次访问，将计数累加到当前分段。
     * @param key 缓存键
     */
    public void record(String key) {
        int[] arr = counters.computeIfAbsent(key, k -> new int[segments]);
        arr[current.get()]++;
    }

    /**
     * 计算近窗口总热度（各分段求和）。
     * @param key 缓存键
     * @return 热度值
     */
    public int heat(String key) {
        int[] arr = counters.get(key);
        if (arr == null) {
            return 0;
        }

        int sum = 0;
        for (int v : arr) {
            sum += v;
        }
        return sum;
    }

    /**
     * 计算热度评级：根据总热度与阈值映射到等级。
     * 阈值来源：properties.hotkey.levelLow/Medium/High。
     * @param key 缓存键
     * @return 热度等级
     */
    public Level level(String key) {
        int h = heat(key);
        if (h >= properties.getHotkey().getLevelHigh()) {
            return Level.HIGH;
        }
        if (h >= properties.getHotkey().getLevelMedium()) {
            return Level.MEDIUM;
        }
        if (h >= properties.getHotkey().getLevelLow()) {
            return Level.LOW;
        }

        return Level.NONE;
    }

    /**
     * 计算公共页面的动态 TTL：基准 TTL + 等级扩展秒数。
     * @param baseTtlSeconds 基准 TTL 秒数
     * @param key 缓存键
     * @return 动态 TTL 秒数
     */
    public int ttlForPublic(int baseTtlSeconds, String key) {
        Level l = level(key);
        return baseTtlSeconds + extendSeconds(l);
    }

    /**
     * 计算“我的发布”页面的动态 TTL：基准 TTL + 等级扩展秒数。
     * @param baseTtlSeconds 基准 TTL 秒数
     * @param key 缓存键
     * @return 动态 TTL 秒数
     */
    public int ttlForMine(int baseTtlSeconds, String key) {
        Level l = level(key);
        return baseTtlSeconds + extendSeconds(l);
    }

    /**
     * 根据热度等级返回扩展秒数。
     * @param l 热度等级
     * @return 扩展秒数
     */
    private int extendSeconds(Level l) {
        return switch (l) {
            case HIGH -> properties.getHotkey().getExtendHighSeconds();
            case MEDIUM -> properties.getHotkey().getExtendMediumSeconds();
            case LOW -> properties.getHotkey().getExtendLowSeconds();
            default -> 0;
        };
    }

    /**
     * 定时轮转当前分段，清零新分段以实现滑动窗口统计。
     * 触发频率由配置 `cache.hotkey.segment-seconds` 指定（单位秒）。
     */
    @Scheduled(fixedRateString = "${cache.hotkey.segment-seconds:10}000")
    public void rotate() {
        int next = (current.get() + 1) % segments;
        current.set(next);
        for (int[] arr : counters.values()) {
            arr[next] = 0;
        }
    }

    /**
     * 重置指定 key 的滑窗计数（全部清零）。
     * 用于手动降级或在配置变更后清理历史热度。
     * @param key 缓存键
     */
    public void reset(String key) {
        int[] arr = counters.get(key);
        if (arr != null) Arrays.fill(arr, 0);
    }
}

