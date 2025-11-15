package com.tongji.counter.event;

import lombok.Data;

@Data
public class CounterEvent {
    private String entityType;
    private String entityId;
    private String metric; // like | fav
    private int idx; // schema index
    private long userId;
    private int delta; // +1 / -1

    public CounterEvent(String entityType, String entityId, String metric, int idx, long userId, int delta) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.metric = metric;
        this.idx = idx;
        this.userId = userId;
        this.delta = delta;
    }

    public static CounterEvent of(String entityType, String entityId, String metric, int idx, long userId, int delta) {
        return new CounterEvent(entityType, entityId, metric, idx, userId, delta);
    }
}