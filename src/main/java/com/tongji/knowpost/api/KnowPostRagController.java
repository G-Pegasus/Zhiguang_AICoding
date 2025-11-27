package com.tongji.knowpost.api;

import com.tongji.llm.rag.RagIndexService;
import com.tongji.llm.rag.RagQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/knowposts")
@Validated
@RequiredArgsConstructor
public class KnowPostRagController {

    private final RagIndexService indexService;
    private final RagQueryService ragQueryService;

    /**
     * 单篇知文 RAG 问答（WebFlux + Flux 流式输出）。
     * 示例：GET /api/v1/knowposts/{id}/qa/stream?question=...&topK=5&maxTokens=1024
     */
    @GetMapping(value = "/{id}/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> qaStream(@PathVariable("id") long id,
                                 @RequestParam("question") String question,
                                 @RequestParam(value = "topK", defaultValue = "5") int topK,
                                 @RequestParam(value = "maxTokens", defaultValue = "1024") int maxTokens) {
        return ragQueryService.streamAnswerFlux(id, question, topK, maxTokens);
    }

    /**
     * 手动触发单篇索引重建（返回重建的切片数）。
     */
    @PostMapping("/{id}/rag/reindex")
    public int reindex(@PathVariable("id") long id) {
        return indexService.reindexSinglePost(id);
    }
}