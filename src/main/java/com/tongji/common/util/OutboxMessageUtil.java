package com.tongji.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outbox 消息解析工具。
 *
 * <p>用于消费 Canal 推送的 binlog JSON 消息，从中提取 outbox 表的行数据（INSERT/UPDATE）。</p>
 */
public final class OutboxMessageUtil {
    /**
     * 工具类禁止实例化。
     */
    private OutboxMessageUtil() {}

    /**
     * 从 Canal 消息中提取 outbox 表的变更行。
     *
     * <p>仅处理：</p>
     * <ul>
     *   <li>table = outbox</li>
     *   <li>type ∈ {INSERT, UPDATE}</li>
     *   <li>data 为数组（每个元素是一行记录的列集合）</li>
     * </ul>
     *
     * @param objectMapper Jackson 解析器
     * @param message Canal JSON 消息
     * @return outbox 行数组；不匹配或解析失败返回空列表
     */
    public static List<JsonNode> extractRows(ObjectMapper objectMapper, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);

            JsonNode table = root.get("table");
            if (table == null || !"outbox".equals(table.asText())) {
                return Collections.emptyList();
            }

            JsonNode type = root.get("type");
            if (type == null || (!"INSERT".equals(type.asText()) && !"UPDATE".equals(type.asText()))) {
                return Collections.emptyList();
            }

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return Collections.emptyList();
            }
            List<JsonNode> rows = new ArrayList<>();
            data.forEach(rows::add);
            return rows;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
