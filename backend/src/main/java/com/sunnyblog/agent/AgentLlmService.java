package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AgentLlmService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AgentCacheUsage cacheUsage;
    private final boolean configured;
    // Model calls are I/O-bound. A dedicated virtual-thread executor avoids
    // the shared ForkJoinPool throughput limit (which is CPU-count bound).
    private final ExecutorService modelExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentLlmService(ObjectProvider<ChatClient.Builder> builderProvider,
                           ObjectMapper objectMapper,
                           AgentCacheUsage cacheUsage,
                           @Value("${spring.ai.openai.api-key:}") String apiKey) {
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
        this.objectMapper = objectMapper;
        this.cacheUsage = cacheUsage;
        this.configured = chatClient != null && apiKey != null && !apiKey.isBlank();
        if (!configured) {
            log.warn("Agent AI is disabled: no model API key configured");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    @PreDestroy
    public void shutdown() {
        modelExecutor.shutdownNow();
    }

    public String complete(String system, String user, int timeoutSeconds) {
        if (!configured) throw new IllegalStateException("Agent 模型未配置，请设置 DEEPSEEK_API_KEY");
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ChatResponse response = CompletableFuture.supplyAsync(() -> chatClient.prompt()
                                .system(system)
                                .user(user)
                                .call()
                                .chatResponse(), modelExecutor)
                        .get(Math.max(10, timeoutSeconds), TimeUnit.SECONDS);
                recordUsage(response);
                String content = response == null || response.getResult() == null
                        || response.getResult().getOutput() == null
                        ? null : response.getResult().getOutput().getContent();
                if (content == null || content.isBlank()) {
                    throw new IllegalStateException("模型返回为空");
                }
                return content.trim();
            } catch (Exception ex) {
                last = ex;
                if (attempt == 1) {
                    log.warn("Agent model call failed (attempt 1), retrying: {}", ex.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.warn("Agent model call failed: {}", last == null ? "unknown" : last.getMessage());
        throw new IllegalStateException("Agent 模型调用失败: "
                + (last == null ? "unknown" : last.getMessage()), last);
    }

    private void recordUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) return;
        Object usage = response.getMetadata().getUsage();
        if (usage == null) return;
        Number prompt = number(usage, "getPromptTokens");
        Number completion = number(usage, "getCompletionTokens");
        if (completion == null) completion = number(usage, "getGenerationTokens");
        Number cached = number(usage, "getPromptTokensDetailsCachedTokens");
        cacheUsage.record(prompt, cached, completion);
    }

    private Number number(Object target, String method) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value instanceof Number n ? n : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public JsonNode completeJson(String system, String user, int timeoutSeconds) {
        String text = complete(system + "\n只输出一个 JSON 对象，不要 markdown 代码块。", user, timeoutSeconds);
        JsonNode node = parseJson(text);
        if (node == null) throw new IllegalStateException("模型没有返回有效 JSON: " + clip(text));
        return node;
    }

    public JsonNode parseJson(String text) {
        if (text == null) return null;
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int start = cleaned.indexOf('\n');
            int end = cleaned.lastIndexOf("```");
            if (start >= 0 && end > start) cleaned = cleaned.substring(start + 1, end).trim();
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ignored) {
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readTree(cleaned.substring(start, end + 1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String clip(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
