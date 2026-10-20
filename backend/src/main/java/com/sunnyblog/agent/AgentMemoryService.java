package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentMemoryService {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    @Data
    public static class MemoryEntry {
        private String id;
        private String role;
        private String content;
        private String importance;
        private String kind;
        private String createdAt;
    }

    @Data
    public static class Session {
        private String id;
        private String title;
        private String createdAt;
        private String updatedAt;
        private List<MemoryEntry> memories = new ArrayList<>();
    }

    public Session getOrCreate(String sessionId) {
        String id = (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : safeId(sessionId);
        Path file = sessionFile(id);
        if (Files.exists(file)) {
            try {
                return objectMapper.readValue(file.toFile(), Session.class);
            } catch (IOException ignored) {
                // corrupted local memory starts a new session with the same id
            }
        }
        Session session = new Session();
        session.setId(id);
        session.setTitle("新的对话");
        session.setCreatedAt(Instant.now().toString());
        session.setUpdatedAt(session.getCreatedAt());
        save(session);
        return session;
    }

    public synchronized MemoryEntry append(String sessionId, String role, String content,
                                           String importance, String kind) {
        Session session = getOrCreate(sessionId);
        MemoryEntry entry = new MemoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setRole(role);
        entry.setContent(content == null ? "" : content.trim());
        entry.setImportance(importance == null ? classify(content) : importance);
        entry.setKind(kind == null ? "chat" : kind);
        entry.setCreatedAt(Instant.now().toString());
        session.getMemories().add(entry);
        if (session.getMemories().size() > 240) {
            session.getMemories().remove(0);
        }
        session.setUpdatedAt(entry.getCreatedAt());
        if ("user".equals(role) && session.getTitle().equals("新的对话")) {
            String title = entry.getContent().replaceAll("\\s+", " ");
            session.setTitle(title.length() > 24 ? title.substring(0, 24) : title);
        }
        save(session);
        return entry;
    }

    /**
     * U-shaped prompt assembly: important memories first, then mid/low history,
     * with the newest user question appended by the caller at the very end.
     */
    public String buildPromptContext(String sessionId, int maxChars) {
        Session session = getOrCreate(sessionId);
        List<MemoryEntry> memories = new ArrayList<>(session.getMemories());
        if (memories.isEmpty()) return "（暂无历史记忆）";
        List<MemoryEntry> high = memories.stream()
                .filter(m -> "HIGH".equals(m.getImportance())).toList();
        List<MemoryEntry> medium = memories.stream()
                .filter(m -> "MEDIUM".equals(m.getImportance())).toList();
        List<MemoryEntry> low = memories.stream()
                .filter(m -> "LOW".equals(m.getImportance())).toList();

        StringBuilder sb = new StringBuilder();
        appendBlock(sb, "高优先级长期记忆", high, maxChars / 3);
        appendBlock(sb, "中优先级记忆", tail(medium, 12), maxChars / 3);
        appendBlock(sb, "近期低优先级对话", tail(low, 10), maxChars / 4);
        String result = sb.toString();
        if (result.length() > maxChars) {
            result = result.substring(Math.max(0, result.length() - maxChars));
        }
        return result.isBlank() ? "（暂无历史记忆）" : result;
    }

    public List<MemoryEntry> history(String sessionId) {
        return getOrCreate(sessionId).getMemories();
    }

    public String classify(String content) {
        if (content == null) return "LOW";
        String text = content.toLowerCase(Locale.ROOT);
        if (text.matches("(?s).*(记住|重要|目标|计划|偏好|以后|必须|长期|不要再|我的名字|我是).*")) {
            return "HIGH";
        }
        if (text.matches("(?s).*(帮我|请|如何|为什么|实现|写一个|分析|任务).*")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    public int importanceRank(String importance) {
        if ("HIGH".equals(importance)) return 3;
        if ("MEDIUM".equals(importance)) return 2;
        return 1;
    }

    private void appendBlock(StringBuilder sb, String title, List<MemoryEntry> entries, int budget) {
        if (entries.isEmpty()) return;
        sb.append("## ").append(title).append("\n");
        int used = 0;
        for (MemoryEntry entry : entries) {
            String line = "- [" + entry.getRole() + "] " + entry.getContent().replace("\n", " ") + "\n";
            if (used + line.length() > budget) break;
            sb.append(line);
            used += line.length();
        }
        sb.append("\n");
    }

    private List<MemoryEntry> tail(List<MemoryEntry> entries, int count) {
        if (entries.size() <= count) return entries;
        return entries.subList(entries.size() - count, entries.size());
    }

    private void save(Session session) {
        Path file = sessionFile(session.getId());
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writeValue(file.toFile(), session);
        } catch (IOException ex) {
            throw new IllegalStateException("保存 Agent 记忆失败", ex);
        }
    }

    private Path sessionFile(String id) {
        Path root = Path.of(properties.getDir()).toAbsolutePath().normalize().resolve("sessions");
        return root.resolve(safeId(id) + ".json");
    }

    private String safeId(String id) {
        String safe = id.replaceAll("[^a-zA-Z0-9\\-]", "");
        return safe.isBlank() ? UUID.randomUUID().toString() : safe;
    }
}
