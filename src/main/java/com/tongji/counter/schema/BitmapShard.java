package com.tongji.counter.schema;

/**
 * 位图分片配置与帮助函数。
 * 采用固定分片大小，避免单键因用户ID偏移过大而膨胀。
 */
public final class BitmapShard {
    // 每个分片的位数（32K 位 => 4KB/分片）
    public static final int CHUNK_SIZE = 32_768;

    public static long chunkOf(long userId) {
        return userId / CHUNK_SIZE;
    }

    public static long bitOf(long userId) {
        return userId % CHUNK_SIZE;
    }

    private BitmapShard() {}
}