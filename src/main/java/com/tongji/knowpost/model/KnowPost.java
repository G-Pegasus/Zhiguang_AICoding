package com.tongji.knowpost.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowPost {
    private Long id;
    private Long tagId;
    /** JSON 字符串，示例：["java","编程"] */
    private String tags;
    private String title;
    private String description;
    private String contentUrl;
    private String contentObjectKey;
    private String contentEtag;
    private Long contentSize;
    private String contentSha256;
    private Long creatorId;
    private Boolean isTop;
    private String type;
    private String visible;
    /** JSON 字符串，示例：["https://...","https://..."] */
    private String imgUrls;
    private String videoUrl;
    private String status;
    private Instant createTime;
    private Instant updateTime;
    private Instant publishTime;
}