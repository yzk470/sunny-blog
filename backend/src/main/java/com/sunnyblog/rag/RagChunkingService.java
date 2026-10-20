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
        FIXED, SENTENCE, PARAGRAPH, HEADING, AGENT
    }

    private final RagProperties properties;
    private final AgentLlmService llm;

    public List<String> chunk(String text, Strategy strategy) {
        return chunk(text, strategy, properties.getMaxChars(), properties.getOverlap());
    }

    public List<String> chunk(String text, Strategy strategy, int requestedSize, int requestedOverlap) {
        String clean = text == null ? "" : text.replace("\r\n", "\n").replaceAll("[ \\t]+", " ").trim();
        if (clean.isBlank()) return List.of();
        int size = Math.max(200, Math.min(4000, requestedSize));
        int overlap = Math.max(0, Math.min(size / 2, requestedOverlap));
        return switch (strategy) {
            case AGENT -> agentChunk(clean, size);
            case HEADING -> headingChunk(clean, size, overlap);
            case PARAGRAPH -> paragraphChunk(clean, size, overlap);
            case SENTENCE -> sentenceChunk(clean, size, overlap);
            default -> fixedChunk(clean, size, overlap);
        };
    }

    private List<String> fixedChunk(String text, int size, int overlap) {
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

    private List<String> sentenceChunk(String text, int size, int overlap) {
        String[] sentences = text.split("(?<=[。！？.!?])\\s*");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            String value = sentence.trim();
            if (value.isBlank()) continue;
            if (current.length() + value.length() + 1 > size && current.length() > 0) {
                chunks.add(current.toString().trim());
                String tail = overlap > 0 ? tail(current.toString(), overlap) : "";
                current.setLength(0);
                if (!tail.isBlank()) current.append(tail).append(' ');
            }
            if (value.length() > size) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                chunks.addAll(fixedChunk(value, size, overlap));
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(value);
            }
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return chunks;
    }

    private List<String> paragraphChunk(String text, int size, int overlap) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String value = paragraph.trim();
            if (value.isBlank()) continue;
            if (current.length() + value.length() + 1 > size && current.length() > 0) {
                chunks.add(current.toString().trim());
                String tail = overlap > 0 ? tail(current.toString(), overlap) : "";
                current.setLength(0);
                if (!tail.isBlank()) current.append(tail).append("\n\n");
            }
            if (value.length() > size) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                chunks.addAll(fixedChunk(value, size, overlap));
            } else {
                if (current.length() > 0) current.append("\n\n");
                current.append(value);
            }
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return chunks;
    }

    private List<String> headingChunk(String text, int size, int overlap) {
        String[] sections = text.split("(?=\\n#{1,4}\\s)|(?=\\n第[一二三四五六七八九十百0-9]+[章节部分])");
        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            String value = section.trim();
            if (value.isBlank()) continue;
            if (value.length() <= size) {
                if (!chunks.isEmpty()) {
                    String previous = chunks.get(chunks.size() - 1);
                    if (previous.length() + value.length() + 2 <= size) {
                        chunks.set(chunks.size() - 1, previous + "\n\n" + value);
                        continue;
                    }
                }
                chunks.add(value);
            } else {
                chunks.addAll(fixedChunk(value, size, overlap));
            }
        }
        return chunks;
    }

    private String tail(String text, int length) {
        if (text.length() <= length) return text;
        return text.substring(text.length() - length);
    }

    private List<String> agentChunk(String text, int targetSize) {
        if (!llm.isConfigured()) return paragraphChunk(text, targetSize, 0);
        String clipped = text.length() > 20000 ? text.substring(0, 20000) : text;
        String system = """
                你是文档切分器。把输入文档切成适合向量检索的语义块。
                每块围绕一个完整主题，目标长度约 %d 个字符，可上下浮动，保留标题和上下文，不要丢内容。
                只输出 JSON：{"chunks":["块1","块2",...]}。
                """.formatted(targetSize);
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
            return chunks.isEmpty() ? paragraphChunk(text, targetSize, 0) : chunks;
        } catch (Exception ex) {
            log.warn("Agent chunking failed, fallback to paragraph: {}", ex.getMessage());
            return paragraphChunk(text, targetSize, 0);
        }
    }
}
