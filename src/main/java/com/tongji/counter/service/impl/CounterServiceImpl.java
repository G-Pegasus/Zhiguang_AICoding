package com.tongji.counter.service.impl;

import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import com.tongji.counter.schema.BitmapShard;
import com.tongji.counter.service.CounterService;
import com.tongji.counter.event.CounterEvent;
import com.tongji.counter.event.CounterEventProducer;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class CounterServiceImpl implements CounterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> toggleScript;
    private final CounterEventProducer eventProducer;
    private final DefaultRedisScript<Long> unlockScript;
    private final ApplicationEventPublisher eventPublisher;

    public CounterServiceImpl(StringRedisTemplate redis, CounterEventProducer eventProducer, ApplicationEventPublisher eventPublisher) {
        this.redis = redis;
        this.eventProducer = eventProducer;
        this.eventPublisher = eventPublisher;
        this.toggleScript = new DefaultRedisScript<>();
        this.toggleScript.setResultType(Long.class);
        this.toggleScript.setScriptText(TOGGLE_LUA);
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setResultType(Long.class);
        this.unlockScript.setScriptText(UNLOCK_LUA);
    }

    @Override
    public boolean like(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", CounterSchema.IDX_LIKE, true);
    }

    @Override
    public boolean unlike(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", CounterSchema.IDX_LIKE, false);
    }

    @Override
    public boolean fav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", CounterSchema.IDX_FAV, true);
    }

    @Override
    public boolean unfav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", CounterSchema.IDX_FAV, false);
    }

    private boolean toggle(String etype, String eid, long uid, String metric, int idx, boolean add) {
        long chunk = BitmapShard.chunkOf(uid);
        long bit = BitmapShard.bitOf(uid);
        String bmKey = CounterKeys.bitmapKey(metric, etype, eid, chunk);
        List<String> keys = List.of(bmKey);
        List<String> args = List.of(String.valueOf(bit), add ? "add" : "remove");
        Long changed = redis.execute(toggleScript, keys, args.toArray());
        boolean ok = changed == 1L;
        if (ok) {
            int delta = add ? 1 : -1;
            eventProducer.publish(CounterEvent.of(etype, eid, metric, idx, uid, delta));
            eventPublisher.publishEvent(CounterEvent.of(etype, eid, metric, idx, uid, delta));
        }
        return ok;
    }

    @Override
    public Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics) {
        String sdsKey = CounterKeys.sdsKey(entityType, entityId);
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        byte[] raw = getRaw(sdsKey);
        boolean needRebuild = (raw == null || raw.length != expectedLen);

        Map<String, Long> result = new LinkedHashMap<>();
        if (needRebuild) {
            // 分布式锁避免并发重建：lock:sds-rebuild:{etype}:{eid}
            String lockKey = String.format("lock:sds-rebuild:%s:%s", entityType, entityId);
            String token = UUID.randomUUID().toString();
            boolean locked = tryLock(lockKey, token, 5000L);
            try {
                // 依据位图分片统计真实计数（管道批量 BITCOUNT）
                byte[] newSds = new byte[expectedLen];
                List<String> rebuildFields = new ArrayList<>();
                for (String m : metrics) {
                    Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                    if (idx == null) continue;
                    long sum = bitCountShardsPipelined(m, entityType, entityId);
                    writeInt32BE(newSds, idx * CounterSchema.FIELD_SIZE, sum);
                    result.put(m, sum);
                    rebuildFields.add(String.valueOf(idx));
                }
                // 仅在拿到锁时回写SDS并清理聚合桶，避免重复加算
                if (locked) {
                    setRaw(sdsKey, newSds);
                    if (!rebuildFields.isEmpty()) {
                        String aggKey = CounterKeys.aggKey(entityType, entityId);
                        redis.opsForHash().delete(aggKey, rebuildFields.toArray());
                    }
                }
            } finally {
                if (locked) {
                    unlock(lockKey, token);
                }
            }
        } else {
            for (String m : metrics) {
                Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                if (idx == null) continue;
                int off = idx * CounterSchema.FIELD_SIZE;
                long val = readInt32BE(raw, off);
                result.put(m, val);
            }
        }
        return result;
    }

    @Override
    public Map<String, Map<String, Long>> getCountsBatch(String entityType, List<String> entityIds, List<String> metrics) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        if (entityIds == null || entityIds.isEmpty() || metrics == null || metrics.isEmpty()) return out;
        List<String> keys = new ArrayList<>(entityIds.size());
        for (String eid : entityIds) {
            keys.add(CounterKeys.sdsKey(entityType, eid));
        }
        List<Object> raws = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String k : keys) {
                connection.stringCommands().get(k.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        for (int i = 0; i < entityIds.size(); i++) {
            String eid = entityIds.get(i);
            Object rawObj = (raws != null && i < raws.size()) ? raws.get(i) : null;
            byte[] raw = (rawObj instanceof byte[]) ? (byte[]) rawObj : null;
            Map<String, Long> m = new LinkedHashMap<>();
            if (raw != null && raw.length == expectedLen) {
                for (String name : metrics) {
                    Integer idx = CounterSchema.NAME_TO_IDX.get(name);
                    if (idx == null) continue;
                    int off = idx * CounterSchema.FIELD_SIZE;
                    long val = readInt32BE(raw, off);
                    m.put(name, val);
                }
            } else {
                for (String name : metrics) {
                    m.put(name, 0L);
                }
            }
            out.put(eid, m);
        }
        return out;
    }

    @Override
    public boolean isLiked(String entityType, String entityId, long userId) {
        long chunk = BitmapShard.chunkOf(userId);
        long bit = BitmapShard.bitOf(userId);
        return getBit(CounterKeys.bitmapKey("like", entityType, entityId, chunk), bit);
    }

    @Override
    public boolean isFaved(String entityType, String entityId, long userId) {
        long chunk = BitmapShard.chunkOf(userId);
        long bit = BitmapShard.bitOf(userId);
        return getBit(CounterKeys.bitmapKey("fav", entityType, entityId, chunk), bit);
    }

    private boolean getBit(String key, long offset) {
        Boolean bit = redis.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().getBit(key.getBytes(StandardCharsets.UTF_8), offset));
        return Boolean.TRUE.equals(bit);
    }

    private byte[] getRaw(String key) {
        return redis.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
    }

    private void setRaw(String key, byte[] val) {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), val);
            return null;
        });
    }

    private boolean tryLock(String key, String token, long ttlMillis) {
        Boolean ok = redis.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().set(
                        key.getBytes(StandardCharsets.UTF_8),
                        token.getBytes(StandardCharsets.UTF_8),
                        Expiration.milliseconds(ttlMillis),
                        RedisStringCommands.SetOption.SET_IF_ABSENT
                ));
        return Boolean.TRUE.equals(ok);
    }

    private void unlock(String key, String token) {
        redis.execute(unlockScript, List.of(key), token);
    }

    private static long readInt32BE(byte[] buf, int off) {
        long n = 0;
        for (int i = 0; i < 4; i++) {
            n = (n << 8) | (buf[off + i] & 0xFFL);
        }
        return n;
    }

    private static void writeInt32BE(byte[] buf, int off, long val) {
        long n = Math.max(0, Math.min(val, 0xFFFF_FFFFL));
        buf[off] = (byte) ((n >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((n >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((n >>> 8) & 0xFF);
        buf[off + 3] = (byte) (n & 0xFF);
    }

    private long bitCountShardsPipelined(String metric, String etype, String eid) {
        String pattern = String.format("bm:%s:%s:%s:*", metric, etype, eid);
        Set<String> keys = redis.keys(pattern);
        if (keys == null || keys.isEmpty()) return 0L;
        List<Object> res = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String k : keys) {
                connection.stringCommands().bitCount(k.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        long sum = 0L;
        if (res != null) {
            for (Object o : res) {
                if (o instanceof Number n) {
                    sum += n.longValue();
                }
            }
        }
        return sum;
    }

    // Redis 内嵌 Lua（Redis 5/6 的 Lua 5.1），位图原子切换（分片内偏移）
    private static final String TOGGLE_LUA = """
            local bmKey = KEYS[1]
            local offset = tonumber(ARGV[1])
            local op = ARGV[2] -- 'add' or 'remove'
            local prev = redis.call('GETBIT', bmKey, offset)
            if op == 'add' then
              if prev == 1 then return 0 end
              redis.call('SETBIT', bmKey, offset, 1)
              return 1
            elseif op == 'remove' then
              if prev == 0 then return 0 end
              redis.call('SETBIT', bmKey, offset, 0)
              return 1
            end
            return -1
            """;

    // 安全释放锁：仅当持有者token匹配时删除
    private static final String UNLOCK_LUA = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            else
              return 0
            end
            """;
}
