package com.tongji.llm.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 问答查询服务：
 * - 在问答前保障索引，检索相关上下文并构造提示词
 * - 通过 ChatClient 以流式（SSE）方式返回模型输出
 */
@Service
@RequiredArgsConstructor
public class RagQueryService {
    // 向量检索接口（Elasticsearch 向量库封装）
    private final VectorStore vectorStore;
    // 大模型对话客户端（在 LlmConfig 中通过 @Qualifier 绑定 deepSeekChatModel）
    private final ChatClient chatClient;
    // 索引服务：确保帖子在问答前已建立/更新索引
    private final RagIndexService indexService;

    /**
     * 使用 WebFlux 返回回答内容的流。
     */
    public Flux<String> streamAnswerFlux(long postId, String question, int topK, int maxTokens) {
        // 轻量保障：如索引不存在或指纹未变更则跳过，否则重建
        indexService.ensureIndexed(postId);

        // 检索上下文：先宽召回，再按 postId 做服务端过滤
        List<String> contexts = searchContexts(String.valueOf(postId), question, Math.max(1, topK));
        // 组装上下文文本，分隔符用于提示词中分块标识
        String context = String.join("\n\n---\n\n", contexts);

        // 系统提示：限定只依据提供的上下文作答，无法确定需明确说明
        String system = "你是中文知识助手。只能依据提供的知文上下文回答；无法确定的请说明不确定。";
        // 用户消息：包含问题和召回到的上下文
        String user = "问题：" + question + "\n\n上下文如下（可能不完整）：\n" + context + "\n\n请基于以上上下文作答。";

        return chatClient
                .prompt() // 构建对话
                .system(system)
                .user(user)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat") // 指定 DeepSeek 模型
                        .temperature(0.2)       // 低温度：更稳健、少发散
                        .maxTokens(maxTokens)    // 控制最大输出长度
                        .build())
                .stream()  // 以流式（SSE）返回模型输出
                .content(); // 转换为 Flux<String>
    }

    /**
     * 语义检索上下文：
     * - 先进行宽召回（fetchK ≥ 3×topK，至少 20）提高召回率
     * - 再按 metadata.postId 做服务端过滤，避免跨帖子污染
     */
    private List<String> searchContexts(String postId, String query, int topK) {
        int fetchK = Math.max(topK * 3, 20); // 宽召回：扩大初始检索集合
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(fetchK).build() // 语义相似检索
        );
        List<String> out = new ArrayList<>(topK);
        for (Document d : docs) {
            Object pid = d.getMetadata().get("postId");
            if (pid != null && postId.equals(String.valueOf(pid))) { // 仅保留当前帖子对应的切片
                String txt = d.getText();
                if (txt != null && !txt.isEmpty()) {
                    out.add(txt);
                    if (out.size() >= topK) break; // 只取前 topK 个上下文
                }
            }
        }
        return out;
    }
}