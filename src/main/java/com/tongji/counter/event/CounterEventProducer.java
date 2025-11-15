package com.tongji.counter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class CounterEventProducer {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public CounterEventProducer(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    public void publish(CounterEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafka.send(CounterTopics.EVENTS, payload);
        } catch (JsonProcessingException e) {
            // 生产异常不抛出影响主流程；可接入告警
        }
    }
}