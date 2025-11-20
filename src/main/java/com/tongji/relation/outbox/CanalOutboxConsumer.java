package com.tongji.relation.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.processor.RelationEventProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Canal Outbox 消费者。
 * 职责：消费 Canal 桥接写入的 outbox 主题消息，提取 payload 并反序列化为 RelationEvent，交由处理器落库与更新缓存/计数；使用手动位点确保处理成功语义。
 */
@Service
public class CanalOutboxConsumer {
    private final ObjectMapper objectMapper;
    private final RelationEventProcessor processor;

    /**
     * Outbox 消费者构造函数。
     * @param objectMapper JSON 序列化器
     * @param processor 关系事件处理器
     */
    public CanalOutboxConsumer(ObjectMapper objectMapper, RelationEventProcessor processor) {
        this.objectMapper = objectMapper;
        this.processor = processor;
    }

    /**
     * 消费 Canal outbox 消息并转为关系事件处理。
     * 监听 Canal→Kafka 桥接写入的 outbox 主题；使用手动位点提交
     * @param message Kafka 消息内容
     * @param ack 位点确认对象
     */
    @KafkaListener(topics = OutboxTopics.CANAL_OUTBOX, groupId = "relation-outbox-consumer")
    public void onMessage(String message, Acknowledgment ack) {
        try {
            // CanalKafkaBridge 发送的统一结构：{"table":"outbox","type":"INSERT|UPDATE","data":[{"payload":"..."}...]}
            JsonNode root = objectMapper.readTree(message);
            JsonNode table = root.get("table");
            // 过滤非 outbox 表的变更
            if (table == null || !"outbox".equals(table.asText())) {
                ack.acknowledge();
                return;
            }

            JsonNode type = root.get("type");
            // 仅处理 INSERT/UPDATE，其他事件直接确认位点
            if (type == null || (!"INSERT".equals(type.asText()) && !"UPDATE".equals(type.asText()))) {
                ack.acknowledge();
                return;
            }

            JsonNode data = root.get("data");
            // data 必须是行数组；否则确认位点避免重复消费
            if (data == null || !data.isArray()) {
                ack.acknowledge();
                return;
            }

            for (JsonNode row : data) {
                JsonNode payloadNode = row.get("payload");
                // 缺少 payload 字段的行跳过
                if (payloadNode == null) continue;
                // payload 是 RelationEvent 的 JSON 字符串
                RelationEvent evt = objectMapper.readValue(payloadNode.asText(), RelationEvent.class);
                // 将 outbox 事件转为领域事件处理（入库/缓存/计数等）
                processor.process(evt);
            }
            // 本批处理完成后提交位点，确保“已处理”的语义
            ack.acknowledge();
        } catch (Exception ignored) {}
    }
}

