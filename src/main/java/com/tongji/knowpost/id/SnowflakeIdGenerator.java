package com.tongji.knowpost.id;

import org.springframework.stereotype.Component;

/**
 * 线程安全的雪花算法 ID 生成器。
 * 41 位时间戳 + 5 位数据中心 + 5 位工作节点 + 12 位序列。
 */
@Component
public class SnowflakeIdGenerator {
    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long datacenterId;
    private final long workerId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator() {
        this(1, 1);
    }

    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId out of range");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = currentTime();

//        if (timestamp < lastTimestamp) {
//            throw new IllegalStateException("Clock moved backwards. Refusing to generate id");
//        }
        // 等待时钟追回的方案
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;

            // 1. 小幅度回拨（比如 NTP 校时导致的 1~5ms 间抖动）：等待一会儿再试
            if (offset <= 5) {
                try {
                    // 睡 offset 毫秒，给系统时钟一点时间“追上来”
                    Thread.sleep(offset);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Thread interrupted while waiting for clock to catch up", e);
                }

                timestamp = currentTime();
                if (timestamp < lastTimestamp) {
                    // 等完还是没追上，说明问题较严重，直接拒绝
                    throw new IllegalStateException(
                            "Clock is still behind after waiting. last=" + lastTimestamp + ", now=" + timestamp);
                }
            } else {
                // 2. 回拨幅度太大，直接拒绝，避免线程长时间阻塞
                throw new IllegalStateException(
                        "Clock moved backwards too much. Refusing to generate id. offset=" + offset + "ms");
            }
        }

        // 处理同一毫秒内的并发请求：序列号逻辑
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 这一毫秒的 4096 个名额用完了
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组装 64 位 ID
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTime();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime();
        }
        return timestamp;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}