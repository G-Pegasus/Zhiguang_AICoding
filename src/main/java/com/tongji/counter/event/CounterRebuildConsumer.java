package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 灾难场景下的计数重建消费者：基于 earliest 回放历史事件，直接折叠到 SDS。
 * 默认关闭，仅当 counter.rebuild.enabled=true 时启用。
 */
@Service
@ConditionalOnProperty(name = "counter.rebuild.enabled", havingValue = "true")
public class CounterRebuildConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;

    public CounterRebuildConsumer(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptText(INCR_FIELD_LUA);
    }

    @KafkaListener(
            topics = CounterTopics.EVENTS,
            groupId = "counter-rebuild",
            properties = {"auto.offset.reset=earliest"}
    )
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        CounterEvent evt = objectMapper.readValue(message, CounterEvent.class);
        String cntKey = CounterKeys.sdsKey(evt.getEntityType(), evt.getEntityId());
        try {
            redis.execute(incrScript, List.of(cntKey),
                    String.valueOf(CounterSchema.SCHEMA_LEN),
                    String.valueOf(CounterSchema.FIELD_SIZE),
                    String.valueOf(evt.getIdx()),
                    String.valueOf(evt.getDelta()));
            ack.acknowledge();
        } catch (Exception ex) {
            // 不提交位点以便重试
        }
    }

    // 复用与聚合消费者一致的原子计数折叠脚本
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