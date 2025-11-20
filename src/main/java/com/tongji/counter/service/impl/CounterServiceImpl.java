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

/**
 * 内容实体计数服务实现（位图事实 + 事件聚合 + SDS 汇总）。
 *
 * <p>职责：</p>
 * - 位图原子切换并产出计数事件（幂等）；
 * - 读取汇总计数（SDS），异常时基于位图分片重建；
 * - 批量读取优化与“是否点赞/收藏”判定。
 */
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
        // 位图状态原子切换，仅在状态变化时返回 1
        this.toggleScript.setScriptText(TOGGLE_LUA);
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setResultType(Long.class);
        // 基于 token 的安全解锁，防误删
        this.unlockScript.setScriptText(UNLOCK_LUA);
    }

    @Override
    /**
     * 点赞：位图原子置位，仅当状态从未点赞→已点赞时返回 true。
     * 同步路径完成事实层更新后产出增量事件，异步聚合到计数快照。
     * @param entityType 实体类型
     * @param entityId 实体 ID
     * @param userId 用户 ID
     * @return 是否发生状态变化（幂等）
     */
    public boolean like(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", CounterSchema.IDX_LIKE, true);
    }

    @Override
    /**
     * 取消点赞：位图原子清零，仅当状态从已点赞→未点赞时返回 true。
     * 产出增量事件（delta=-1），异步聚合到计数快照。
     */
    public boolean unlike(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", CounterSchema.IDX_LIKE, false);
    }

    @Override
    /**
     * 收藏：位图原子置位，并产出增量事件（delta=+1）。
     */
    public boolean fav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", CounterSchema.IDX_FAV, true);
    }

    @Override
    /**
     * 取消收藏：位图原子清零，并产出增量事件（delta=-1）。
     */
    public boolean unfav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", CounterSchema.IDX_FAV, false);
    }

    /**
     * 位图状态切换：仅在状态变化时返回成功，并产出增量事件。
     * @param etype 实体类型
     * @param eid 实体 ID
     * @param uid 用户 ID
     * @param metric 指标名称（like/fav）
     * @param idx 指标索引（用于 SDS 固定结构定位）
     * @param add 是否置位（true=添加，false=移除）
     */
    private boolean toggle(String etype, String eid, long uid, String metric, int idx, boolean add) {
        // 固定分片定位：按用户ID映射到 chunk 与分片内 bit 偏移，避免单键膨胀与热点
        long chunk = BitmapShard.chunkOf(uid);
        // 分片内位偏移
        long bit = BitmapShard.bitOf(uid);
        String bmKey = CounterKeys.bitmapKey(metric, etype, eid, chunk);
        List<String> keys = List.of(bmKey);
        List<String> args = List.of(String.valueOf(bit), add ? "add" : "remove");
        Long changed = redis.execute(toggleScript, keys, args.toArray());
        boolean ok = changed == 1L;
        if (ok) {
            int delta = add ? 1 : -1;
            // 产出计数事件（异步聚合），分区按实体维度保证同实体事件顺序
            eventProducer.publish(CounterEvent.of(etype, eid, metric, idx, uid, delta));
            // 本地事件：触发缓存失效/旁路更新等快速路径
            eventPublisher.publishEvent(CounterEvent.of(etype, eid, metric, idx, uid, delta));
        }
        return ok;
    }

    /**
     * 获取实体计数汇总（SDS）。
     * 若缺失或结构异常则触发基于位图的事实重建，并清理对应聚合字段。
     */
    @Override
    public Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics) {
        String sdsKey = CounterKeys.sdsKey(entityType, entityId);
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        // SDS 固定结构：按大端 32 位编码
        byte[] raw = getRaw(sdsKey);
        boolean needRebuild = (raw == null || raw.length != expectedLen);

        Map<String, Long> result = new LinkedHashMap<>();
        if (needRebuild) {
            // 分布式锁避免并发重建：lock:sds-rebuild:{etype}:{eid}
            String lockKey = String.format("lock:sds-rebuild:%s:%s", entityType, entityId); // 防并发重建
            String token = UUID.randomUUID().toString();
            boolean locked = tryLock(lockKey, token, 5000L);
            try {
                // 依据位图分片统计真实计数（管道批量 BITCOUNT）
                byte[] newSds = new byte[expectedLen];
                List<String> rebuildFields = new ArrayList<>();
                for (String m : metrics) {
                    Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                    if (idx == null) {
                        continue;
                    }
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
                        // 清理对应字段，杜绝重复折叠
                        redis.opsForHash().delete(aggKey, rebuildFields.toArray());
                    }
                }
            } finally {
                if (locked) {
                    unlock(lockKey, token); // 仅持有者释放
                }
            }
        } else {
            for (String m : metrics) {
                Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                if (idx == null) continue;
                int off = idx * CounterSchema.FIELD_SIZE;
                long val = readInt32BE(raw, off); // 大端读取单段 32 位值
                result.put(m, val);
            }
        }
        return result;
    }

    /**
     * 批量获取实体计数汇总（管道批量 GET）。
     * 缺失或异常结构时补零。
     */
    @Override
    /**
     * 批量获取实体计数（管道批量 GET 降低 RTT）。
     * 缺失或结构异常（长度不符）时按零返回，保证接口稳定。
     * @param entityType 实体类型
     * @param entityIds 实体ID列表
     * @param metrics 指标名列表
     * @return 每个实体的指标计数映射
     */
    public Map<String, Map<String, Long>> getCountsBatch(String entityType, List<String> entityIds, List<String> metrics) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        if (entityIds == null || entityIds.isEmpty() || metrics == null || metrics.isEmpty()) return out;
        List<String> keys = new ArrayList<>(entityIds.size());
        for (String eid : entityIds) {
            keys.add(CounterKeys.sdsKey(entityType, eid));
        }

        // 管道批量 GET：将多个 SDS 读取合并到一次往返
        List<Object> raws = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String k : keys) {
                connection.stringCommands().get(k.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        int expectedLen = CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE;
        for (int i = 0; i < entityIds.size(); i++) {
            String eid = entityIds.get(i);
            Object rawObj = i < raws.size() ? raws.get(i) : null;
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
                    m.put(name, 0L); // 缺失或异常结构时补零，避免接口失败与重建风暴
                }
            }
            out.put(eid, m);
        }
        return out;
    }

    @Override
    /**
     * 是否点赞判定：基于分片位图在分片内做位测试。
     * 毫秒级读取，不依赖计数快照。
     */
    public boolean isLiked(String entityType, String entityId, long userId) {
        long chunk = BitmapShard.chunkOf(userId);
        long bit = BitmapShard.bitOf(userId);
        return getBit(CounterKeys.bitmapKey("like", entityType, entityId, chunk), bit);
    }

    @Override
    /**
     * 是否收藏判定：同点赞，基于分片位图位测试。
     */
    public boolean isFaved(String entityType, String entityId, long userId) {
        long chunk = BitmapShard.chunkOf(userId);
        long bit = BitmapShard.bitOf(userId);
        return getBit(CounterKeys.bitmapKey("fav", entityType, entityId, chunk), bit);
    }

    /**
     * 读取位图某偏移位（GETBIT）。
     * @param key 位图分片键
     * @param offset 分片内位偏移
     * @return 位是否为 1
     */
    private boolean getBit(String key, long offset) {
        Boolean bit = redis.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().getBit(key.getBytes(StandardCharsets.UTF_8), offset));
        return Boolean.TRUE.equals(bit);
    }

    /**
     * 读取 SDS 原始字节（固定结构，长度=字段数×4）。
     */
    private byte[] getRaw(String key) {
        return redis.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 写入 SDS 原始字节（覆盖式写）。
     */
    private void setRaw(String key, byte[] val) {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), val);
            return null;
        });
    }

    /**
     * 尝试加分布式锁（SET NX EX），用于防并发重建。
     * @param key 锁键
     * @param token 持有者令牌
     * @param ttlMillis 锁 TTL（毫秒）
     */
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

    /**
     * 安全释放锁：仅持有者令牌匹配时删除。
     */
    private void unlock(String key, String token) {
        redis.execute(unlockScript, List.of(key), token);
    }

    /**
     * 以大端序读取 32 位无符号整型。
     */
    private static long readInt32BE(byte[] buf, int off) {
        long n = 0;
        for (int i = 0; i < 4; i++) {
            n = (n << 8) | (buf[off + i] & 0xFFL);
        }
        return n;
    }

    /**
     * 以大端序写入 32 位无符号整型（截断到 0~2^32-1）。
     */
    private static void writeInt32BE(byte[] buf, int off, long val) {
        long n = Math.max(0, Math.min(val, 0xFFFF_FFFFL));
        buf[off] = (byte) ((n >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((n >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((n >>> 8) & 0xFF);
        buf[off + 3] = (byte) (n & 0xFF);
    }

    /**
     * 基于位图分片进行管道化 BITCOUNT 汇总，用于按事实重建计数。
     * 说明：当前使用 KEYS 枚举分片（生产建议维护索引集合），结果按分片 BITCOUNT 求和。
     */
    private long bitCountShardsPipelined(String metric, String etype, String eid) {
        String pattern = String.format("bm:%s:%s:%s:*", metric, etype, eid);
        // 生产环境建议以索引集合替代 KEYS
        Set<String> keys = redis.keys(pattern); 
        if (keys.isEmpty()) return 0L;

        // 管道批量 BITCOUNT 汇总
        List<Object> res = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String k : keys) {
                connection.stringCommands().bitCount(k.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        long sum = 0L;

        for (Object o : res) {
            if (o instanceof Number n) {
                sum += n.longValue();
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
