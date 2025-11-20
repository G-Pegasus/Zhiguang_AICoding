package com.tongji.counter.config;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 计数模块配置：启用调度与 Kafka，并提供字符串模板。
 */
@Configuration
@EnableScheduling // 启用 @Scheduled 定时任务（计数聚合刷写）
@EnableKafka // 启用 Kafka（计数事件生产与消费）
public class CounterConfig {

    @Bean
    public ProducerFactory<String, String> stringProducerFactory(KafkaProperties properties) {
        var props = properties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new StringSerializer()); // 统一字符串序列化
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }
}