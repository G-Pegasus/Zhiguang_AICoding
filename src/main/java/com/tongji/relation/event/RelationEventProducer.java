package com.tongji.relation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class RelationEventProducer {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    /**
     * 事件生产者构造函数。
     * @param kafka Kafka 模板
     * @param objectMapper JSON 序列化器
     */
    public RelationEventProducer(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布关系事件到 Kafka。
     * @param evt 关系事件
     */
    public void publish(RelationEvent evt) {
        try {
            String payload = objectMapper.writeValueAsString(evt);
            kafka.send(RelationTopics.EVENTS, payload);
        } catch (Exception ignored) {}
    }
}

