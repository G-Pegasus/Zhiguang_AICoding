package com.tongji.knowpost.model;

import lombok.Data;

import java.time.Instant;

/**
 * 知文详情查询的行映射（含作者信息）。
 */
@Data
public class KnowPostDetailRow {
    private Long id;
    private Long creatorId;
    private String title;
    private String description;
    private String tags;        // JSON 字符串
    private String imgUrls;     // JSON 字符串
    private String contentUrl;
    private String contentEtag;
    private String contentSha256;
    private String authorAvatar;
    private String authorNickname;
    private String authorTagJson;
    private Instant publishTime;
    private Boolean isTop;
    private String visible;
    private String type;
    private String status;
}