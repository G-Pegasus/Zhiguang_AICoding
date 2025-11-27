package com.tongji.llm.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.config.EsProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RagIndexService {
    private static final Logger log = LoggerFactory.getLogger(RagIndexService.class);
    private final VectorStore vectorStore;
    private final KnowPostMapper knowPostMapper;
    private final RestTemplate http = new RestTemplate();
    private final ElasticsearchClient es;
    private final EsProperties esProps;

    public void ensureIndexed(long postId) {
        reindexSinglePost(postId);
    }

    public int reindexSinglePost(long postId) {
        KnowPostDetailRow row = knowPostMapper.findDetailById(postId);
        if (row == null) {
            log.warn("Post {} not found", postId);
            return 0;
        }

        // 仅索引公开的已发布知文
        if (!"published".equalsIgnoreCase(row.getStatus()) || !"public".equalsIgnoreCase(row.getVisible())) {
            log.warn("Post {} is not public/published, skip indexing", postId);
            return 0;
        }

        if (!StringUtils.hasText(row.getContentUrl())) {
            log.warn("Post {} missing contentUrl or not found", postId);
            return 0;
        }

        // 指纹检测：如未变化则跳过重建
        String currentSha = row.getContentSha256();
        String currentEtag = row.getContentEtag();
        if (isUpToDate(postId, currentSha, currentEtag)) {
            log.info("Post {} already indexed with same fingerprint, skip", postId);
            return 0;
        }

        String text = fetchContent(row.getContentUrl());
        if (!StringUtils.hasText(text)) {
            log.warn("Post {} content empty", postId);
            return 0;
        }

        List<String> chunks = chunkMarkdown(text);
        // 幂等 upsert：先删除旧切片
        deleteExistingChunks(postId);

        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String cid = postId + "#" + i;
            Map<String, Object> meta = new HashMap<>();
            meta.put("postId", String.valueOf(postId));
            meta.put("chunkId", cid);
            meta.put("position", i);
            meta.put("contentEtag", currentEtag);
            meta.put("contentSha256", currentSha);
            meta.put("contentUrl", row.getContentUrl());
            meta.put("title", row.getTitle());
            docs.add(new Document(chunks.get(i), meta));
        }
        try {
            vectorStore.add(docs);
        } catch (Exception e) {
            log.error("VectorStore add failed: {}", e.getMessage());
            return 0;
        }
        return docs.size();
    }

    private boolean isUpToDate(long postId, String currentSha, String currentEtag) {
        try {
            if (!StringUtils.hasText(esProps.getIndex())) {
                // 未配置索引名则无法判断，直接视为需要重建
                return false;
            }
            SearchResponse<Map> resp = es.search(s -> s
                            .index(esProps.getIndex())
                            .size(1)
                            .query(q -> q.term(t -> t
                                    .field("metadata.postId")
                                    .value(v -> v.stringValue(String.valueOf(postId))))),
                    Map.class);
            List<Hit<Map>> hits = resp.hits().hits();
            if (hits == null || hits.isEmpty()) return false;
            Map source = hits.getFirst().source();
            if (source == null) return false;
            Object metaObj = source.get("metadata");
            if (!(metaObj instanceof Map<?, ?> meta)) return false;
            String indexedSha = asString(meta.get("contentSha256"));
            String indexedEtag = asString(meta.get("contentEtag"));
            if (StringUtils.hasText(currentSha) && StringUtils.hasText(indexedSha)) {
                return Objects.equals(currentSha, indexedSha);
            }
            if (StringUtils.hasText(currentEtag) && StringUtils.hasText(indexedEtag)) {
                return Objects.equals(currentEtag, indexedEtag);
            }
            return false;
        } catch (Exception e) {
            log.warn("Fingerprint check failed for post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    private void deleteExistingChunks(long postId) {
        try {
            if (!StringUtils.hasText(esProps.getIndex())) return;
            es.deleteByQuery(d -> d
                    .index(esProps.getIndex())
                    .query(q -> q.term(t -> t
                            .field("metadata.postId")
                            .value(v -> v.stringValue(String.valueOf(postId))))));
        } catch (Exception e) {
            log.warn("Delete old chunks failed for post {}: {}", postId, e.getMessage());
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String fetchContent(String url) {
        try {
            return http.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("Fetch content failed: {}", e.getMessage());
            return null;
        }
    }

    private List<String> chunkMarkdown(String text) {
        List<String> paras = new ArrayList<>();
        String[] lines = text.split("\r?\n");
        StringBuilder buf = new StringBuilder();
        for (String line : lines) {
            boolean isHeader = line.startsWith("#");
            if (isHeader && !buf.isEmpty()) {
                paras.add(buf.toString());
                buf.setLength(0);
            }
            buf.append(line).append('\n');
        }
        if (!buf.isEmpty()) paras.add(buf.toString());

        return getChunks(paras);
    }

    private static List<String> getChunks(List<String> paras) {
        List<String> chunks = new ArrayList<>();
        for (String p : paras) {
            if (p.length() <= 800) {
                chunks.add(p);
            } else {
                int start = 0;
                while (start < p.length()) {
                    int end = Math.min(start + 800, p.length());
                    chunks.add(p.substring(start, end));
                    if (end >= p.length()) break;
                    start = Math.max(end - 100, start + 1);
                }
            }
        }
        return chunks;
    }
}