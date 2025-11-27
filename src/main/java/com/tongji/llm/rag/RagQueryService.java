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

@Service
@RequiredArgsConstructor
public class RagQueryService {
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagIndexService indexService;

    /**
     * 使用 WebFlux 返回回答内容的流。
     */
    public Flux<String> streamAnswerFlux(long postId, String question, int topK, int maxTokens) {
        indexService.ensureIndexed(postId);

        List<String> contexts = searchContexts(String.valueOf(postId), question, Math.max(1, topK));
        String context = String.join("\n\n---\n\n", contexts);

        String system = "你是中文知识助手。只能依据提供的知文上下文回答；无法确定的请说明不确定。";
        String user = "问题：" + question + "\n\n上下文如下（可能不完整）：\n" + context + "\n\n请基于以上上下文作答。";

        return chatClient
                .prompt()
                .system(system)
                .user(user)
                .options(DeepSeekChatOptions.builder().model("deepseek-chat").temperature(0.2).maxTokens(maxTokens).build())
                .stream()
                .content();
    }

    private List<String> searchContexts(String postId, String query, int topK) {
        int fetchK = Math.max(topK * 3, 20);
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(fetchK).build()
        );
        List<String> out = new ArrayList<>(topK);
        for (Document d : docs) {
            Object pid = d.getMetadata().get("postId");
            if (pid != null && postId.equals(String.valueOf(pid))) {
                String txt = d.getText();
                if (txt != null && !txt.isEmpty()) {
                    out.add(txt);
                    if (out.size() >= topK) break;
                }
            }
        }
        return out;
    }
}