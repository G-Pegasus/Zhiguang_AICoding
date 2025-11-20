package com.tongji.relation.event;

import lombok.Data;

/**
 * 关系领域事件模型。
 * 字段：type（FollowCreated/FollowCanceled）、fromUserId（触发方）、toUserId（目标方）、id（关系记录ID，可为空）。
 * 用途：经 Outbox 持久化与 Canal 转发后，供消费者处理入库、缓存与计数更新。
 */
@Data
public class RelationEvent {
    private String type;
    private Long fromUserId;
    private Long toUserId;
    private Long id;

    /**
     * 构造关系事件。
     * @param type 事件类型
     * @param fromUserId 触发方用户ID
     * @param toUserId 目标方用户ID
     * @param id 关系记录ID，可为空
     */
    public RelationEvent(String type, Long fromUserId, Long toUserId, Long id) {
        this.type = type;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.id = id;
    }
}

