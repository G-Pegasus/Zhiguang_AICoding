package com.tongji.knowpost.model;

import lombok.Data;

import java.time.Instant;

/**
 * Mapper 原始行映射（从 DB 读取）。
 */
@Data
public class KnowPostFeedRow {
    private Long id;
    private String title;
    private String description;
    private String tags;       // JSON 字符串
    private String imgUrls;    // JSON 字符串
    private String authorAvatar;
    private String authorNickname;
    private String authorTagJson; // 作者的领域标签 JSON
    private Instant publishTime;
    private Boolean isTop;
}