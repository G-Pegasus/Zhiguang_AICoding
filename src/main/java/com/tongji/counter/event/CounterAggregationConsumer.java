package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.List;

@Service
public class CounterAggregationConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;

    // 使用 Redis Hash 作为持久化聚合桶：agg:{schema}:{etype}:{eid} ，field=idx ，value=delta

    public CounterAggregationConsumer(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptText(INCR_FIELD_LUA);
    }

    @KafkaListener(topics = CounterTopics.EVENTS, groupId = "counter-agg")
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        CounterEvent evt = objectMapper.readValue(message, CounterEvent.class);
        String aggKey = CounterKeys.aggKey(evt.getEntityType(), evt.getEntityId());
        String field = String.valueOf(evt.getIdx());
        try {
            // 将增量持久化到 Redis Hash
            redis.opsForHash().increment(aggKey, field, evt.getDelta());
            // 成功后提交位点，绑定“已持久化”语义
            ack.acknowledge();
        } catch (Exception ex) {
            // 不提交位点以便重试
        }
    }

    @Scheduled(fixedDelay = 1000L)
    public void flush() {
        // 简化实现：扫描所有聚合桶键（可后续优化为索引集合）
        Set<String> keys = redis.keys("agg:" + CounterSchema.SCHEMA_ID + ":*");
        if (keys == null || keys.isEmpty()) return;
        for (String aggKey : keys) {
            Map<Object, Object> entries = redis.opsForHash().entries(aggKey);
            if (entries == null || entries.isEmpty()) continue;
            // 解析 etype/eid 以定位 SDS key
            String[] parts = aggKey.split(":", 4); // agg:schema:etype:eid
            if (parts.length < 4) continue;
            String cntKey = CounterKeys.sdsKey(parts[2], parts[3]);
            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                String field = String.valueOf(e.getKey());
                long delta;
                try {
                    delta = Long.parseLong(String.valueOf(e.getValue()));
                } catch (NumberFormatException nfe) {
                    continue;
                }
                if (delta == 0) continue;
                int idx;
                try {
                    idx = Integer.parseInt(field);
                } catch (NumberFormatException nfe) {
                    continue;
                }
                try {
                    redis.execute(incrScript, List.of(cntKey),
                            String.valueOf(CounterSchema.SCHEMA_LEN),
                            String.valueOf(CounterSchema.FIELD_SIZE),
                            String.valueOf(idx),
                            String.valueOf(delta));
                    // 成功后删除该字段，避免重复加算
                    redis.opsForHash().delete(aggKey, field);
                } catch (Exception ex) {
                    // 留存字段，下一轮重试
                }
            }
            // 如 Hash 已为空，删除聚合桶Key
            Long size = redis.opsForHash().size(aggKey);
            if (size != null && size == 0L) {
                redis.delete(aggKey);
            }
        }
    }

    private static final String INCR_FIELD_LUA = "\n" +
            "local cntKey = KEYS[1]\n" +
            "local schemaLen = tonumber(ARGV[1])\n" +
            "local fieldSize = tonumber(ARGV[2]) -- 固定为4\n" +
            "local idx = tonumber(ARGV[3])\n" +
            "local delta = tonumber(ARGV[4])\n" +
            "\n" +
            "local function read32be(s, off)\n" +
            "  local b = {string.byte(s, off+1, off+4)}\n" +
            "  local n = 0\n" +
            "  for i=1,4 do n = n * 256 + b[i] end\n" +
            "  return n\n" +
            "end\n" +
            "\n" +
            "local function write32be(n)\n" +
            "  local t = {}\n" +
            "  for i=4,1,-1 do t[i] = n % 256; n = math.floor(n/256) end\n" +
            "  return string.char(unpack(t))\n" +
            "end\n" +
            "\n" +
            "local cnt = redis.call('GET', cntKey)\n" +
            "if not cnt then cnt = string.rep(string.char(0), schemaLen * fieldSize) end\n" +
            "local off = idx * fieldSize\n" +
            "local v = read32be(cnt, off) + delta\n" +
            "if v < 0 then v = 0 end\n" +
            "local seg = write32be(v)\n" +
            "cnt = string.sub(cnt, 1, off) .. seg .. string.sub(cnt, off+fieldSize+1)\n" +
            "redis.call('SET', cntKey, cnt)\n" +
            "return 1\n";
}