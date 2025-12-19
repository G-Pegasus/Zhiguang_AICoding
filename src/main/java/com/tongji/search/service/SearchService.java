package com.tongji.search.service;

import com.tongji.search.api.dto.SearchResponse;
import com.tongji.search.api.dto.SuggestResponse;

/**
 * 搜索服务接口：封装检索与联想能力，供控制器调用。
 */
public interface SearchService {
    /**
     * 关键词检索。
     * @param q 关键词
     * @param size 返回条数
     * @param tagsCsv 标签过滤（CSV）
     * @param after 游标（Base64URL）
     * @param currentUserIdNullable 当前用户ID（可空）
     */
    SearchResponse search(String q, int size, String tagsCsv, String after, Long currentUserIdNullable);
    /**
     * 联想建议（Completion Suggester）。
     * @param prefix 前缀
     * @param size 返回条数
     */
    SuggestResponse suggest(String prefix, int size);
}