package com.tongji.search.api;

import com.tongji.search.api.dto.SearchResponse;
import com.tongji.search.api.dto.SuggestResponse;
import com.tongji.search.service.SearchService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.tongji.auth.token.JwtService;

/**
 * 搜索接口控制器：
 * - 提供关键词检索与联想建议两个 GET API
 * - 参数通过 Spring Validation 进行基本校验
 */
@RestController
@RequestMapping("/api/v1/search")
@Validated
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final JwtService jwtService;

    /**
     * 关键词检索。
     * @param q 关键词（必填）
     * @param size 返回条数（默认 20，最小 1）
     * @param tagsCsv 标签过滤（逗号分隔，可选）
     * @param after 游标（Base64URL，可选）
     */
    @GetMapping
    public SearchResponse search(@RequestParam("q") @NotBlank String q,
                                 @RequestParam(value = "size", required = false, defaultValue = "20") @Min(1) int size,
                                 @RequestParam(value = "tags", required = false) String tagsCsv,
                                 @RequestParam(value = "after", required = false) String after,
                                 @AuthenticationPrincipal Jwt jwt) {
        Long userId = (jwt == null) ? null : jwtService.extractUserId(jwt);
        return searchService.search(q, size, tagsCsv, after, userId);
    }

    /**
     * 联想建议（Completion Suggester）。
     * @param prefix 前缀（必填）
     * @param size 返回条数（默认 10，最小 1）
     */
    @GetMapping("/suggest")
    public SuggestResponse suggest(@RequestParam("prefix") @NotBlank String prefix,
                                   @RequestParam(value = "size", required = false, defaultValue = "10") @Min(1) int size) {
        return searchService.suggest(prefix, size);
    }
}