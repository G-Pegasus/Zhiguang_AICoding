package com.tongji.counter.schema;

/**
 * 用户维度计数键生成工具。
 */
public final class UserCounterKeys {
    private UserCounterKeys() {}

    public static String sdsKey(long userId) {
        return "ucnt:" + userId; // 用户维度固定结构计数（SDS）键
    }
}

