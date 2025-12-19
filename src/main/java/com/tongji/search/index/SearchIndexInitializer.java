package com.tongji.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.CompletionProperty;
import co.elastic.clients.elasticsearch._types.mapping.DateProperty;
import co.elastic.clients.elasticsearch._types.mapping.IntegerNumberProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.LongNumberProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

/**
 * 搜索索引初始化：应用启动时确保索引与 Mapping 存在。
 * 注意：title/body 使用 IK 分词器，需在 ES 集群安装 analysis-ik 插件。
 */
@Service
@RequiredArgsConstructor
public class SearchIndexInitializer {
    private final ElasticsearchClient es;
    private static final String INDEX = "zhiguang_content_index";

    @PostConstruct
    public void ensureIndex() {
        try {
            boolean exists = es.indices().exists(e -> e.index(INDEX)).value();
            if (exists) {
                return;
            }

            es.indices().create(c -> c.index(INDEX).mappings(m -> m
                    .properties("content_id", Property.of(p -> p.long_(LongNumberProperty.of(b -> b))))
                    .properties("content_type", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("description", Property.of(p -> p.text(TextProperty.of(b -> b.analyzer("ik_max_word")))))
                    // IK 分词：title 使用 ik_max_word，检索使用 ik_smart；body 使用 ik_max_word
                    .properties("title", Property.of(p -> p.text(TextProperty.of(b -> b.analyzer("ik_max_word").searchAnalyzer("ik_smart")))))
                    .properties("body", Property.of(p -> p.text(TextProperty.of(b -> b.analyzer("ik_max_word")))))
                    .properties("tags", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("author_id", Property.of(p -> p.long_(LongNumberProperty.of(b -> b))))
                    .properties("author_avatar", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("author_nickname", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("author_tag_json", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("publish_time", Property.of(p -> p.date(DateProperty.of(b -> b))))
                    .properties("like_count", Property.of(p -> p.integer(IntegerNumberProperty.of(b -> b))))
                    .properties("favorite_count", Property.of(p -> p.integer(IntegerNumberProperty.of(b -> b))))
                    .properties("view_count", Property.of(p -> p.integer(IntegerNumberProperty.of(b -> b))))
                    .properties("status", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("img_urls", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("is_top", Property.of(p -> p.keyword(KeywordProperty.of(b -> b))))
                    .properties("title_suggest", Property.of(p -> p.completion(CompletionProperty.of(b -> b)))
                    )));
        } catch (Exception ignored) {
            // 忽略异常以保证应用启动；索引可能由后续写入动态创建，但 Mapping 将不完整
        }
    }
}