package com.tongji.relation.event;

/**
 * 构造关系事件。
 *
 * @param type       事件类型
 * @param fromUserId 触发方用户ID
 * @param toUserId   目标方用户ID
 * @param id         关系记录ID，可为空
 */
public record RelationEvent(
        String type,
        Long fromUserId,
        Long toUserId,
        Long id) {
}
