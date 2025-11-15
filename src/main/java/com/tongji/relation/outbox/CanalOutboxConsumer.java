package com.tongji.relation.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.processor.RelationEventProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

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
     * @param message Kafka 消息内容
     * @param ack 位点确认对象
     */
    @KafkaListener(topics = OutboxTopics.CANAL_OUTBOX, groupId = "relation-outbox-consumer")
    public void onMessage(String message, Acknowledgment ack) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode table = root.get("table");
            if (table == null || !"outbox".equals(table.asText())) {
                ack.acknowledge();
                return;
            }

            JsonNode type = root.get("type");
            if (type == null || (!"INSERT".equals(type.asText()) && !"UPDATE".equals(type.asText()))) {
                ack.acknowledge();
                return;
            }

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                ack.acknowledge();
                return;
            }

            for (JsonNode row : data) {
                JsonNode payloadNode = row.get("payload");
                if (payloadNode == null) continue;
                RelationEvent evt = objectMapper.readValue(payloadNode.asText(), RelationEvent.class);
                processor.process(evt);
            }
            ack.acknowledge();
        } catch (Exception ignored) {}
    }
}

