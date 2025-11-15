package com.tongji.knowpost.service.impl;

import com.tongji.knowpost.service.KnowPostFeedService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.knowpost.api.dto.FeedItemResponse;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostFeedRow;
import com.tongji.counter.service.CounterService;
import com.github.benmanes.caffeine.cache.Cache;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.cache.config.CacheProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class KnowPostFeedServiceImpl implements KnowPostFeedService {

    private final KnowPostMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CounterService counterService;
    private final Cache<String, FeedPageResponse> feedPublicCache;
    private final Cache<String, FeedPageResponse> feedMineCache;
    private final HotKeyDetector hotKey;
    private static final Logger log = LoggerFactory.getLogger(KnowPostFeedServiceImpl.class);
    private static final int LAYOUT_VER = 1;
    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();

    @Autowired
    public KnowPostFeedServiceImpl(
            KnowPostMapper mapper,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CounterService counterService,
            @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache,
            @Qualifier("feedMineCache") Cache<String, FeedPageResponse> feedMineCache,
            HotKeyDetector hotKey
    ) {
        this.mapper = mapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.counterService = counterService;
        this.feedPublicCache = feedPublicCache;
        this.feedMineCache = feedMineCache;
        this.hotKey = hotKey;
    }

    private String cacheKey(int page, int size) {
        return "feed:public:" + size + ":" + page + ":v" + LAYOUT_VER;
    }

    /**
     * 获取公开的首页 Feed（分页，旁路缓存）。
     */
    public FeedPageResponse getPublicFeed(int page, int size, Long currentUserIdNullable) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        String key = cacheKey(safePage, safeSize);
        long hourSlot = System.currentTimeMillis() / 3600000L;
        String idsKey = "feed:public:ids:" + safeSize + ":" + hourSlot + ":" + safePage;
        String hasMoreKey = "feed:public:ids:" + safeSize + ":" + hourSlot + ":" + safePage + ":hasMore";

        FeedPageResponse local = feedPublicCache.getIfPresent(key);
        if (local != null) {
            hotKey.record(key);
            maybeExtendTtlPublic(key);
            log.info("feed.public source=local key={} page={} size={}", key, safePage, safeSize);
            List<FeedItemResponse> enrichedLocal = enrich(local.items(), currentUserIdNullable);
            return new FeedPageResponse(enrichedLocal, local.page(), local.size(), local.hasMore());
        }

        FeedPageResponse fromCache = assembleFromCache(idsKey, hasMoreKey, safePage, safeSize, currentUserIdNullable);
        if (fromCache != null) {
            feedPublicCache.put(key, fromCache);
            hotKey.record(key);
            maybeExtendTtlPublic(key);
            log.info("feed.public source=3tier key={} page={} size={}", key, safePage, safeSize);
            return fromCache;
        }
        
        // 先查缓存
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                FeedPageResponse cachedResp = objectMapper.readValue(cached, FeedPageResponse.class);
                boolean hasCounts = cachedResp.items() != null && cachedResp.items().stream()
                        .allMatch(it -> it.likeCount() != null && it.favoriteCount() != null);
                if (hasCounts) {
                    // 覆盖用户维度状态，不写回缓存（避免混淆不同用户）
                    feedPublicCache.put(key, cachedResp);
                    hotKey.record(key);
                    maybeExtendTtlPublic(key);
                    log.info("feed.public source=page key={} page={} size={}", key, safePage, safeSize);
                    CompletableFuture.runAsync(() -> repairFragmentsFromPage(cachedResp, idsKey, hasMoreKey, safePage, safeSize));
                    List<FeedItemResponse> enriched = new ArrayList<>(cachedResp.items().size());
                    for (FeedItemResponse it : cachedResp.items()) {
                        boolean liked = currentUserIdNullable != null &&
                                counterService.isLiked("knowpost", it.id(), currentUserIdNullable);
                        boolean faved = currentUserIdNullable != null &&
                                counterService.isFaved("knowpost", it.id(), currentUserIdNullable);
                        enriched.add(new FeedItemResponse(
                                it.id(),
                                it.title(),
                                it.description(),
                                it.coverImage(),
                                it.tags(),
                                it.authorAvatar(),
                                it.authorNickname(),
                                it.tagJson(),
                                it.likeCount(),
                                it.favoriteCount(),
                                liked,
                                faved
                        ));
                    }
                    return new FeedPageResponse(enriched, cachedResp.page(), cachedResp.size(), cachedResp.hasMore());
                }
                // 若缓存缺少计数字段，回源构建并覆盖缓存
            } catch (Exception ignored) {
                // 反序列化失败则走数据库并覆盖缓存
            }
        }

        Object lock = singleFlight.computeIfAbsent(idsKey, k -> new Object());
        synchronized (lock) {
            // 重查三层缓存，避免重复回源
            FeedPageResponse again = assembleFromCache(idsKey, hasMoreKey, safePage, safeSize, currentUserIdNullable);
            if (again != null) {
                feedPublicCache.put(key, again);
                hotKey.record(key);
                maybeExtendTtlPublic(key);
                log.info("feed.public source=3tier(after-flight) key={} page={} size={}", key, safePage, safeSize);
                singleFlight.remove(idsKey);
                return again;
            }

            int offset = (safePage - 1) * safeSize;
            List<KnowPostFeedRow> rows = mapper.listFeedPublic(safeSize + 1, offset);
            boolean hasMore = rows.size() > safeSize;
            if (hasMore) {
                rows = rows.subList(0, safeSize);
            }

            // 构建基础列表（计数），liked/faved 置为 null，用于缓存
            List<FeedItemResponse> items = new ArrayList<>(rows.size());
            for (KnowPostFeedRow r : rows) {
                List<String> tags = parseStringArray(r.getTags());
                List<String> imgs = parseStringArray(r.getImgUrls());
                String cover = imgs.isEmpty() ? null : imgs.getFirst();
                Map<String, Long> counts = counterService.getCounts("knowpost", String.valueOf(r.getId()), List.of("like", "fav"));
                Long likeCount = counts.getOrDefault("like", 0L);
                Long favoriteCount = counts.getOrDefault("fav", 0L);
                items.add(new FeedItemResponse(
                        String.valueOf(r.getId()),
                        r.getTitle(),
                        r.getDescription(),
                        cover,
                        tags,
                        r.getAuthorAvatar(),
                        r.getAuthorNickname(),
                        r.getAuthorTagJson(),
                        likeCount,
                        favoriteCount,
                        null,
                        null
                ));
            }

            FeedPageResponse respForCache = new FeedPageResponse(items, safePage, safeSize, hasMore);
            int baseTtl = 60;
            int jitter = ThreadLocalRandom.current().nextInt(30);
            Duration frTtl = Duration.ofSeconds(baseTtl + jitter);
            Duration pageTtl = Duration.ofSeconds(10 + ThreadLocalRandom.current().nextInt(11));
            writeCaches(key, idsKey, hasMoreKey, safePage, safeSize, rows, items, hasMore, frTtl, pageTtl);
            feedPublicCache.put(key, respForCache);
            hotKey.record(key);
            // 返回时覆盖用户维度状态，不写回缓存
            List<FeedItemResponse> enriched = enrich(items, currentUserIdNullable);
            log.info("feed.public source=db key={} page={} size={} hasMore={}", key, safePage, safeSize, hasMore);
            singleFlight.remove(idsKey);
            return new FeedPageResponse(enriched, safePage, safeSize, hasMore);
        }
    }

    private List<FeedItemResponse> enrich(List<FeedItemResponse> base, Long uid) {
        List<FeedItemResponse> out = new ArrayList<>(base.size());
        for (FeedItemResponse it : base) {
            boolean liked = uid != null && counterService.isLiked("knowpost", it.id(), uid);
            boolean faved = uid != null && counterService.isFaved("knowpost", it.id(), uid);
            out.add(new FeedItemResponse(
                    it.id(), it.title(), it.description(), it.coverImage(), it.tags(), it.authorAvatar(), it.authorNickname(), it.tagJson(), it.likeCount(), it.favoriteCount(), liked, faved
            ));
        }
        return out;
    }

    private FeedPageResponse assembleFromCache(String idsKey, String hasMoreKey, int page, int size, Long uid) {
        List<String> idList = redis.opsForList().range(idsKey, 0, size - 1);
        String hasMoreStr = redis.opsForValue().get(hasMoreKey);
        if (idList == null || idList.isEmpty()) return null;
        List<String> itemKeys = new ArrayList<>(idList.size());
        for (String id : idList) itemKeys.add("feed:item:" + id);
        List<String> countKeys = new ArrayList<>(idList.size());
        for (String id : idList) countKeys.add("feed:count:" + id);
        List<String> itemJsons = redis.opsForValue().multiGet(itemKeys);
        List<String> countJsons = redis.opsForValue().multiGet(countKeys);
        List<FeedItemResponse> items = new ArrayList<>(idList.size());
        List<String> missingIds = new ArrayList<>();
        for (int i = 0; i < idList.size(); i++) {
            String id = idList.get(i);
            String ij = itemJsons != null && i < itemJsons.size() ? itemJsons.get(i) : null;
            FeedItemResponse base = null;
            if (ij != null) {
                if ("NULL".equals(ij)) {
                    items.add(null);
                    continue;
                }
                try { base = objectMapper.readValue(ij, FeedItemResponse.class); } catch (Exception ignored) {}
            }
            if (base == null) missingIds.add(id);
            items.add(base);
        }
        if (!missingIds.isEmpty()) {
            for (String mid : missingIds) {
                Long nid = Long.parseLong(mid);
                com.tongji.knowpost.model.KnowPostDetailRow d = mapper.findDetailById(nid);
                if (d == null) {
                    String kNull = "feed:item:" + mid;
                    Long ttl = redis.getExpire(idsKey);
                    redis.opsForValue().set(kNull, "NULL", Duration.ofSeconds(30 + ThreadLocalRandom.current().nextInt(31)));
                    continue;
                }
                List<String> tags = parseStringArray(d.getTags());
                List<String> imgs = parseStringArray(d.getImgUrls());
                String cover = imgs.isEmpty() ? null : imgs.getFirst();
                FeedItemResponse it = new FeedItemResponse(String.valueOf(d.getId()), d.getTitle(), d.getDescription(), cover, tags, d.getAuthorAvatar(), d.getAuthorNickname(), d.getAuthorTagJson(), null, null, null, null);
                String k = "feed:item:" + mid;
                try {
                    String j = objectMapper.writeValueAsString(it);
                    Long ttl = redis.getExpire(idsKey);
                    if (ttl != null && ttl > 0) redis.opsForValue().set(k, j, Duration.ofSeconds(ttl)); else redis.opsForValue().set(k, j);
                } catch (Exception ignored) {}
                int idx = idList.indexOf(mid);
                if (idx >= 0) items.set(idx, it);
            }
        }
        List<Map<String, Long>> countVals = new ArrayList<>(idList.size());
        for (int i = 0; i < idList.size(); i++) {
            String cj = countJsons != null && i < countJsons.size() ? countJsons.get(i) : null;
            Map<String, Long> cm = null;
            if (cj != null) {
                try { cm = objectMapper.readValue(cj, new TypeReference<>() {
                }); } catch (Exception ignored) {}
            }
            countVals.add(cm);
        }
        List<String> needCountsIds = new ArrayList<>();
        for (int i = 0; i < idList.size(); i++) {
            if (countVals.get(i) == null) needCountsIds.add(idList.get(i));
        }
        if (!needCountsIds.isEmpty()) {
            Map<String, Map<String, Long>> batch = counterService.getCountsBatch("knowpost", needCountsIds, List.of("like","fav"));
            for (String nid : needCountsIds) {
                Map<String, Long> m = batch.getOrDefault(nid, Map.of("like",0L,"fav",0L));
                String k = "feed:count:" + nid;
                try {
                    String j = objectMapper.writeValueAsString(m);
                    Long ttl = redis.getExpire(idsKey);
                    if (ttl != null && ttl > 0) redis.opsForValue().set(k, j, Duration.ofSeconds(ttl)); else redis.opsForValue().set(k, j);
                } catch (Exception ignored) {}
                int idx = idList.indexOf(nid);
                if (idx >= 0) countVals.set(idx, m);
            }
        }
        List<FeedItemResponse> enriched = new ArrayList<>(idList.size());
        for (int i = 0; i < idList.size(); i++) {
            FeedItemResponse base = items.get(i);
            if (base == null) continue;
            Map<String, Long> m = countVals.get(i);
            Long likeCount = m != null ? m.getOrDefault("like", 0L) : 0L;
            Long favoriteCount = m != null ? m.getOrDefault("fav", 0L) : 0L;
            boolean liked = uid != null && counterService.isLiked("knowpost", base.id(), uid);
            boolean faved = uid != null && counterService.isFaved("knowpost", base.id(), uid);
            enriched.add(new FeedItemResponse(base.id(), base.title(), base.description(), base.coverImage(), base.tags(), base.authorAvatar(), base.authorNickname(), base.tagJson(), likeCount, favoriteCount, liked, faved));
        }
        boolean hasMore = hasMoreStr != null ? "1".equals(hasMoreStr) : (idList.size() == size);
        return new FeedPageResponse(enriched, page, size, hasMore);
    }

    private void writeCaches(String pageKey, String idsKey, String hasMoreKey, int page, int size, List<KnowPostFeedRow> rows, List<FeedItemResponse> items, boolean hasMore, Duration frTtl, Duration pageTtl) {
        try {
            String json = objectMapper.writeValueAsString(new FeedPageResponse(items, page, size, hasMore));
            redis.opsForValue().set(pageKey, json, pageTtl);
        } catch (Exception ignored) {}
        List<String> idVals = new ArrayList<>();
        for (KnowPostFeedRow r : rows) idVals.add(String.valueOf(r.getId()));
        if (!idVals.isEmpty()) {
            redis.opsForList().leftPushAll(idsKey, idVals);
            redis.expire(idsKey, frTtl);
            // 软缓存 hasMore：仅在满页时缓存 true，TTL 很短
            if (idVals.size() == size && hasMore) {
                redis.opsForValue().set(hasMoreKey, "1", Duration.ofSeconds(10 + ThreadLocalRandom.current().nextInt(11)));
            } else {
                redis.opsForValue().set(hasMoreKey, hasMore ? "1" : "0", Duration.ofSeconds(10));
            }
        }
        redis.opsForSet().add("feed:public:pages", pageKey);
        for (FeedItemResponse it : items) {
            long hourSlot = System.currentTimeMillis() / 3600000L;
            String idxKey = "feed:public:index:" + it.id() + ":" + hourSlot;
            redis.opsForSet().add(idxKey, pageKey);
            redis.expire(idxKey, frTtl);
            try {
                String itemKey = "feed:item:" + it.id();
                String itemJson = objectMapper.writeValueAsString(it);
                redis.opsForValue().set(itemKey, itemJson, frTtl);
                String cntKey = "feed:count:" + it.id();
                Map<String, Long> cnt = Map.of("like", it.likeCount() == null ? 0L : it.likeCount(), "fav", it.favoriteCount() == null ? 0L : it.favoriteCount());
                String cntJson = objectMapper.writeValueAsString(cnt);
                redis.opsForValue().set(cntKey, cntJson, frTtl);
            } catch (Exception ignored) {}
        }
    }

    private void repairFragmentsFromPage(FeedPageResponse page, String idsKey, String hasMoreKey, int safePage, int safeSize) {
        try {
            int baseTtl = 60;
            int jitter = ThreadLocalRandom.current().nextInt(30);
            Duration frTtl = Duration.ofSeconds(baseTtl + jitter);
            List<String> idVals = new ArrayList<>();
            for (FeedItemResponse it : page.items()) idVals.add(it.id());
            if (!idVals.isEmpty()) {
                redis.opsForList().leftPushAll(idsKey, idVals);
                redis.expire(idsKey, frTtl);
                boolean hasMore = page.hasMore();
                if (idVals.size() == safeSize && hasMore) {
                    redis.opsForValue().set(hasMoreKey, "1", Duration.ofSeconds(10 + ThreadLocalRandom.current().nextInt(11)));
                } else {
                    redis.opsForValue().set(hasMoreKey, hasMore ? "1" : "0", Duration.ofSeconds(10));
                }
            }
            long hourSlot = System.currentTimeMillis() / 3600000L;
            for (FeedItemResponse it : page.items()) {
                String itemKey = "feed:item:" + it.id();
                String cntKey = "feed:count:" + it.id();
                String idxKey = "feed:public:index:" + it.id() + ":" + hourSlot;
                try {
                    String itemJson = objectMapper.writeValueAsString(it);
                    redis.opsForValue().set(itemKey, itemJson, frTtl);
                    Map<String, Long> cnt = Map.of("like", it.likeCount() == null ? 0L : it.likeCount(), "fav", it.favoriteCount() == null ? 0L : it.favoriteCount());
                    String cntJson = objectMapper.writeValueAsString(cnt);
                    redis.opsForValue().set(cntKey, cntJson, frTtl);
                } catch (Exception ignored) {}
                redis.opsForSet().add(idxKey, cacheKey(safePage, safeSize));
                redis.expire(idxKey, frTtl);
            }
            log.info("feed.public fragments repaired idsKey={}", idsKey);
        } catch (Exception ignored) {}
    }

    private String myCacheKey(long userId, int page, int size) {
        return "feed:mine:" + userId + ":" + size + ":" + page;
    }

    /**
     * 获取当前用户自己发布的知文列表（分页，旁路缓存）。
     */
    public FeedPageResponse getMyPublished(long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        String key = myCacheKey(userId, safePage, safeSize);

        FeedPageResponse local = feedMineCache.getIfPresent(key);
        if (local != null) {
            hotKey.record(key);
            maybeExtendTtlMine(key);
            log.info("feed.mine source=local key={} page={} size={} user={}", key, safePage, safeSize, userId);
            return local;
        }

        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                FeedPageResponse cachedResp = objectMapper.readValue(cached, FeedPageResponse.class);
                boolean hasCounts = cachedResp.items() != null && cachedResp.items().stream()
                        .allMatch(it -> it.likeCount() != null && it.favoriteCount() != null);
                if (hasCounts) {
                    // 覆盖 liked/faved，确保老缓存也能返回用户维度状态
                    feedMineCache.put(key, cachedResp);
                    hotKey.record(key);
                    maybeExtendTtlMine(key);
                    log.info("feed.mine source=page key={} page={} size={} user={}", key, safePage, safeSize, userId);
                    List<FeedItemResponse> enriched = new ArrayList<>(cachedResp.items().size());
                    for (FeedItemResponse it : cachedResp.items()) {
                        boolean liked = counterService.isLiked("knowpost", it.id(), userId);
                        boolean faved = counterService.isFaved("knowpost", it.id(), userId);
                        enriched.add(new FeedItemResponse(
                                it.id(),
                                it.title(),
                                it.description(),
                                it.coverImage(),
                                it.tags(),
                                it.authorAvatar(),
                                it.authorNickname(),
                                it.tagJson(),
                                it.likeCount(),
                                it.favoriteCount(),
                                liked,
                                faved
                        ));
                    }
                    return new FeedPageResponse(enriched, cachedResp.page(), cachedResp.size(), cachedResp.hasMore());
                }
            } catch (Exception ignored) {}
        }

        int offset = (safePage - 1) * safeSize;
        List<KnowPostFeedRow> rows = mapper.listMyPublished(userId, safeSize + 1, offset);
        boolean hasMore = rows.size() > safeSize;
        if (hasMore) rows = rows.subList(0, safeSize);

        List<FeedItemResponse> items = new ArrayList<>(rows.size());
        for (KnowPostFeedRow r : rows) {
            List<String> tags = parseStringArray(r.getTags());
            List<String> imgs = parseStringArray(r.getImgUrls());
            String cover = imgs.isEmpty() ? null : imgs.getFirst();
            Map<String, Long> counts = counterService.getCounts("knowpost", String.valueOf(r.getId()), List.of("like", "fav"));
            Long likeCount = counts.getOrDefault("like", 0L);
            Long favoriteCount = counts.getOrDefault("fav", 0L);
            boolean liked = counterService.isLiked("knowpost", String.valueOf(r.getId()), userId);
            boolean faved = counterService.isFaved("knowpost", String.valueOf(r.getId()), userId);
            items.add(new FeedItemResponse(
                    String.valueOf(r.getId()),
                    r.getTitle(),
                    r.getDescription(),
                    cover,
                    tags,
                    r.getAuthorAvatar(),
                    r.getAuthorNickname(),
                    r.getAuthorTagJson(),
                    likeCount,
                    favoriteCount,
                    liked,
                    faved
            ));
        }

        FeedPageResponse resp = new FeedPageResponse(items, safePage, safeSize, hasMore);
        try {
            String json = objectMapper.writeValueAsString(resp);
            int baseTtl = 30; // 用户维度列表缓存更短
            int jitter = ThreadLocalRandom.current().nextInt(20);
            redis.opsForValue().set(key, json, Duration.ofSeconds(baseTtl + jitter));
            feedMineCache.put(key, resp);
            hotKey.record(key);
        } catch (Exception ignored) {}
        log.info("feed.mine source=db key={} page={} size={} user={} hasMore={}", key, safePage, safeSize, userId, hasMore);
        return resp;
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void maybeExtendTtlPublic(String key) {
        int baseTtl = 60;
        int target = hotKey.ttlForPublic(baseTtl, key);
        Long currentTtl = redis.getExpire(key);
        if (currentTtl == null || currentTtl < target) {
            redis.expire(key, Duration.ofSeconds(target));
        }
    }

    private void maybeExtendTtlMine(String key) {
        int baseTtl = 30;
        int target = hotKey.ttlForMine(baseTtl, key);
        Long currentTtl = redis.getExpire(key);
        if (currentTtl == null || currentTtl < target) {
            redis.expire(key, Duration.ofSeconds(target));
        }
    }
}
