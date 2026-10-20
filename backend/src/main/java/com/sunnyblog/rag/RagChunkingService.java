package com.sunnyblog.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunnyblog.agent.AgentLlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChunkingService {

    public enum Strategy {
        FIXED, PARAGRAPH, AGENT
    }

    private final RagProperties properties;
    private final AgentLlmService llm;

    public List<String> chunk(String text, Strategy strategy) {
        String clean = text == null ? "" : text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").trim();
        if (clean.isBlank()) return List.of();
        return switch (strategy) {
            case AGENT -> agentChunk(clean);
            case PARAGRAPH -> paragraphChunk(clean);
            default -> fixedChunk(clean);
        };
    }

    private List<String> fixedChunk(String text) {
        int size = Math.max(200, properties.getMaxChars());
        int overlap = Math.max(0, Math.min(properties.getOverlap(), size / 2));
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            if (end < text.length()) {
                int boundary = lastBoundary(text, start, end);
                if (boundary > start + size / 2) end = boundary;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }

    private int lastBoundary(String text, int start, int end) {
        for (int i = end; i > start + 100; i--) {
            char c = text.charAt(i - 1);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i;
            }
        }
        return end;
    }

    private List<String> paragraphChunk(String text) {
        int size = Math.max(200, properties.getMaxChars());
        String[] paragraphs = text.split("\\n\\s*\\n|(?=\\n#{1,4}\\s)");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String value = paragraph.trim();
            if (value.isBlank()) continue;
            if (current.length() + value.length() + 1 > size && current.length() > 0) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (value.length() > size) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                chunks.addAll(fixedChunk(value));
            } else {
                if (current.length() > 0) current.append("\n\n");
                current.append(value);
            }
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return chunks;
    }

    private List<String> agentChunk(String text) {
        if (!llm.isConfigured()) return paragraphChunk(text);
        String clipped = text.length() > 20000 ? text.substring(0, 20000) : text;
        String system = """
                你是文档切分器。把输入文档切成适合向量检索的语义块。
                每块围绕一个完整主题，长度约 300～900 个中文字符，保留标题和上下文，不要丢内容。
                只输出 JSON：{"chunks":["块1","块2",...]}。
                """;
        try {
            JsonNode json = llm.completeJson(system, "文档：\n" + clipped, 90);
            JsonNode array = json.path("chunks");
            List<String> chunks = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode node : array) {
                    String chunk = node.asText("").trim();
                    if (!chunk.isBlank()) chunks.add(chunk);
                }
            }
            return chunks.isEmpty() ? paragraphChunk(text) : chunks;
        } catch (Exception ex) {
            log.warn("Agent chunking failed, fallback to paragraph: {}", ex.getMessage());
            return paragraphChunk(text);
        }
    }
}
