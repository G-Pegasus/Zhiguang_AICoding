package com.tongji.counter.api.dto;

import lombok.Data;

import java.util.Map;

/**
 * 计数响应体：返回实体类型、ID 及各指标的计数值。
 */
@Data
public class CountsResponse {
    private String entityType;
    private String entityId;
    private Map<String, Long> counts;

    /**
     * 构造响应。
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param counts 指标到计数值的映射
     */
    public CountsResponse(String entityType, String entityId, Map<String, Long> counts) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.counts = counts;
    }
}