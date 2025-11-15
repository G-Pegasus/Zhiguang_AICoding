package com.tongji.counter.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CountsResponse {
    private String entityType;
    private String entityId;
    private Map<String, Long> counts;

    public CountsResponse(String entityType, String entityId, Map<String, Long> counts) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.counts = counts;
    }
}