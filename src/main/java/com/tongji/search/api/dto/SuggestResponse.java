package com.tongji.search.api.dto;

import java.util.List;

/**
 * 联想响应：返回候选标题列表。
 */
public record SuggestResponse(
        List<String> items
) {}