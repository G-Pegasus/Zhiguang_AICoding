package com.tongji.relation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.relation.mapper.RelationMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class RelationCacheConsumer {
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final RelationMapper mapper;

    /**
     * 缓存消费者构造函数。
     * @param objectMapper JSON 序列化器
     * @param redis Redis 客户端
     * @param mapper 关系表访问
     */
    public RelationCacheConsumer(ObjectMapper objectMapper, StringRedisTemplate redis, RelationMapper mapper) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.mapper = mapper;
    }

    @KafkaListener(topics = RelationTopics.EVENTS, groupId = "relation-cache")
    /**
     * 处理关系事件消息：更新粉丝/关注缓存与关系表。
     * @param message Kafka 消息内容
     * @param ack 位点确认对象
     */
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        RelationEvent evt = objectMapper.readValue(message, RelationEvent.class);
        if (evt.getType().equals("FollowCreated")) {
            mapper.insertFollower(evt.getId(), evt.getToUserId(), evt.getFromUserId(), 1);
            long now = System.currentTimeMillis();
            redis.opsForZSet().add("uf:flws:" + evt.getFromUserId(), String.valueOf(evt.getToUserId()), now);
            redis.opsForZSet().add("uf:fans:" + evt.getToUserId(), String.valueOf(evt.getFromUserId()), now);
            ack.acknowledge();
        } else if (evt.getType().equals("FollowCanceled")) {
            mapper.cancelFollower(evt.getToUserId(), evt.getFromUserId());
            redis.opsForZSet().remove("uf:flws:" + evt.getFromUserId(), String.valueOf(evt.getToUserId()));
            redis.opsForZSet().remove("uf:fans:" + evt.getToUserId(), String.valueOf(evt.getFromUserId()));
            ack.acknowledge();
        }
    }
}

