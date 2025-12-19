package com.tongji.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch._types.Refresh;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.tongji.knowpost.model.KnowPostFeedRow;

/**
 * 搜索索引写入服务：负责 upsert/软删 以及首次启动的索引回灌。
 */
@Service
@RequiredArgsConstructor
public class SearchIndexService {
    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final String INDEX = "zhiguang_content_index";

    private final ElasticsearchClient es;
    private final KnowPostMapper knowPostMapper;
    private final CounterService counterService;
    private final ObjectMapper objectMapper;
    private final RestTemplate http = new RestTemplate();

    /**
     * 启动时若索引为空，进行历史数据回灌（分页）。
     */
    @PostConstruct
    public void ensureBackfill() {
        try {
            long cnt = es.count(c -> c.index(INDEX)).count();
            if (cnt > 0) return;
            int limit = 500;
            int offset = 0;
            while (true) {
                List<KnowPostFeedRow> rows = knowPostMapper.listFeedPublic(limit, offset);
                if (rows == null || rows.isEmpty()) {
                    // 没有更多数据，结束回灌
                    break;
                }
                for (KnowPostFeedRow r : rows) {
                    upsertKnowPost(r.getId());
                }
                offset += rows.size();
            }
            log.info("Search index backfill completed: {} documents", es.count(c -> c.index(INDEX)).count());
        } catch (Exception e) {
            log.warn("Search index backfill skipped: {}", e.getMessage());
        }
    }

    /**
     * upsert 内容文档：写入基础字段、计数与补全。使用 wait_for 刷新以保障“立即可搜”。
     */
    public void upsertKnowPost(long id) {
        try {
            KnowPostDetailRow row = knowPostMapper.findDetailById(id);
            if (row == null) {
                log.warn("Index upsert skipped: post {} not found", id);
                return;
            }
            Map<String, Object> doc = new HashMap<>();
            doc.put("content_id", row.getId());
            doc.put("content_type", row.getType());
            doc.put("title", row.getTitle());
            doc.put("description", row.getDescription());
            doc.put("author_id", row.getCreatorId());
            doc.put("author_avatar", row.getAuthorAvatar());
            doc.put("author_nickname", row.getAuthorNickname());
            doc.put("author_tag_json", row.getAuthorTagJson());
            if (row.getPublishTime() != null) {
                doc.put("publish_time", row.getPublishTime().toEpochMilli());
            }
            doc.put("status", row.getStatus());
            doc.put("tags", parseStringArray(row.getTags()));
            doc.put("img_urls", parseStringArray(row.getImgUrls()));
            if (row.getIsTop() != null) {
                doc.put("is_top", row.getIsTop());
            }

            // 正文优先拉取 contentUrl，失败则使用描述
            String body = fetchContentSafe(row.getContentUrl());
            if (body == null || body.isBlank()) {
                body = row.getDescription();
            }
            if (body != null) {
                doc.put("body", truncate(body, 4000));
            }

            Map<String, Long> counts = counterService.getCounts("knowpost", String.valueOf(id), List.of("like","fav"));
            doc.put("like_count", counts.getOrDefault("like", 0L));
            doc.put("favorite_count", counts.getOrDefault("fav", 0L));
            doc.put("view_count", 0L);

            if (row.getTitle() != null && !row.getTitle().isBlank()) {
                doc.put("title_suggest", row.getTitle());
            }

            // 刷新策略：wait_for，保证写入后即刻可检索
            IndexRequest<Map<String, Object>> req = IndexRequest.of(b -> b
                    .index(INDEX)
                    .id(String.valueOf(id))
                    .document(doc)
                    .refresh(Refresh.WaitFor)
            );
            IndexResponse resp = es.index(req);
            log.info("Indexed post {} result={} version={}", id, resp.result(), resp.version());
        } catch (Exception e) {
            log.error("Index upsert failed for post {}: {}", id, e.getMessage());
        }
    }

    /**
     * 软删内容：仅更新 status=deleted，同一文档 ID 覆盖写入。
     */
    public void softDeleteKnowPost(long id) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("content_id", id);
            doc.put("status", "deleted");
            IndexRequest<Map<String, Object>> req = IndexRequest.of(b -> b
                    .index(INDEX)
                    .id(String.valueOf(id))
                    .document(doc)
                    .refresh(Refresh.WaitFor)
            );
            es.index(req);
        } catch (Exception e) {
            log.error("Index soft delete failed for post {}: {}", id, e.getMessage());
        }
    }

    /**
     * 安全拉取正文内容：失败返回 null，不中断索引流程。
     */
    private String fetchContentSafe(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
            ResponseEntity<byte[]> resp = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] bytes = resp.getBody();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            MediaType contentType = resp.getHeaders().getContentType();
            Charset charset = (contentType != null && contentType.getCharset() != null) ? contentType.getCharset() : StandardCharsets.UTF_8;
            return new String(bytes, charset);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 截断字符串到最大长度。
     */
    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }

        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 将 JSON 数组字符串解析为 List<String>；异常返回空列表。
     */
    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
