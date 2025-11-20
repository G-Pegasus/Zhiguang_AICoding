package com.tongji.counter.event;

/**
 * Kafka 主题常量。
 */
public final class CounterTopics {
    public static final String EVENTS = "counter-events"; // 计数事件主题（点赞/收藏等增量）
    private CounterTopics() {}
}