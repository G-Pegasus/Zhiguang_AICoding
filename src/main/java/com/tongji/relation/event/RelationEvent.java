package com.tongji.relation.event;

import lombok.Data;

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

