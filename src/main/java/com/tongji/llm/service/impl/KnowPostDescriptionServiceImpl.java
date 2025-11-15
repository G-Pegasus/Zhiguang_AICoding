package com.tongji.llm.service.impl;

import com.tongji.llm.service.KnowPostDescriptionService;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.stereotype.Service;

import java.text.Normalizer;

@Service
@RequiredArgsConstructor
public class KnowPostDescriptionServiceImpl implements KnowPostDescriptionService {

    private final ChatClient chatClient;

    /**
     * 基于正文生成不超过 50 字的中文描述。
     */
    public String generateDescription(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正文内容不能为空");
        }
        String system = "你是中文文案编辑。请基于用户提供的知文正文，生成一个中文描述，简洁有吸引力，且不超过50个汉字。不输出解释或多段，只输出结果。";
        String user = "正文如下：\n\n" + content + "\n\n请直接给出不超过50字的中文描述。";

        try {
            String result = chatClient
                    .prompt()
                    .system(system)
                    .user(user)
                    .options(DeepSeekChatOptions.builder()
                            .model("deepseek-chat")
                            .temperature(0.8)
                            .maxTokens(120)
                            .build())
                    .call()
                    .content();
            return postProcess(result);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "大模型调用失败: " + e.getMessage());
        }
    }

    private String postProcess(String text) {
        if (text == null) {
            return "";
        }
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("\r\n|\r|\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 去掉可能的前后引号或多余标点
        t = t.replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "")
             .replaceAll("[。!！?？；;、]+$", "");

        // 截断至 50 字（按 code point 计数）
        int limit = 50;
        int count = t.codePointCount(0, t.length());
        if (count <= limit) {
            return t;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0, added = 0;
        while (i < t.length() && added < limit) {
            int cp = t.codePointAt(i);
            sb.appendCodePoint(cp);
            i += Character.charCount(cp);
            added++;
        }
        return sb.toString();
    }
}