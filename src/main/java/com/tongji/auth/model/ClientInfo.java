package com.tongji.auth.model;

/**
 * 客户端信息。
 * <p>
 * 记录客户端 IP 与 User-Agent，用于登录审计、风控与活动记录。
 * 该对象通常由控制器从 HTTP 请求中解析生成。
 *
 * @param ip        客户端 IP 地址（可能来自 `X-Forwarded-For` 或远端地址）。
 * @param userAgent 客户端 User-Agent 字符串。
 */
public record ClientInfo(String ip, String userAgent) {
}
