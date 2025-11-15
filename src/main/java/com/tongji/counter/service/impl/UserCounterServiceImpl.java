package com.tongji.counter.service.impl;

import com.tongji.counter.schema.UserCounterKeys;
import com.tongji.counter.service.UserCounterService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserCounterServiceImpl implements UserCounterService {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;

    public UserCounterServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptText(INCR_FIELD_LUA);
    }

    @Override
    public void incrementFollowings(long userId, int delta) {
        String key = UserCounterKeys.sdsKey(userId);
        redis.execute(incrScript, List.of(key), "5", "4", "1", String.valueOf(delta));
    }

    @Override
    public void incrementFollowers(long userId, int delta) {
        String key = UserCounterKeys.sdsKey(userId);
        redis.execute(incrScript, List.of(key), "5", "4", "2", String.valueOf(delta));
    }

    private static final String INCR_FIELD_LUA = "\n" +
            "local cntKey = KEYS[1]\n" +
            "local schemaLen = tonumber(ARGV[1])\n" +
            "local fieldSize = tonumber(ARGV[2])\n" +
            "local idx = tonumber(ARGV[3])\n" +
            "local delta = tonumber(ARGV[4])\n" +
            "local function read32be(s, off)\n" +
            "  local b = {string.byte(s, off+1, off+4)}\n" +
            "  local n = 0\n" +
            "  for i=1,4 do n = n * 256 + b[i] end\n" +
            "  return n\n" +
            "end\n" +
            "local function write32be(n)\n" +
            "  local t = {}\n" +
            "  for i=4,1,-1 do t[i] = n % 256; n = math.floor(n/256) end\n" +
            "  return string.char(unpack(t))\n" +
            "end\n" +
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

