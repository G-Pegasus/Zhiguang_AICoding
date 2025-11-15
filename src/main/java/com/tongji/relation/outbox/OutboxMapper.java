package com.tongji.relation.outbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboxMapper {
    /**
     * 写入 Outbox 事件。
     * @param id 事件ID
     * @param aggregateType 聚合类型
     * @param aggregateId 聚合ID
     * @param type 事件类型
     * @param payload 事件负载（JSON）
     * @return 影响行数
     */
    int insert(@Param("id") Long id,
               @Param("aggregateType") String aggregateType,
               @Param("aggregateId") Long aggregateId,
               @Param("type") String type,
               @Param("payload") String payload);
}

