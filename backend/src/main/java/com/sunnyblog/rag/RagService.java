package com.sunnyblog.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunnyblog.agent.AgentLlmService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final RagProperties properties;
    private final RagEmbeddingService embeddings;
    private final RagRedisStore redis;
    private final RagTextExtractor extractor;
    private final RagChunkingService chunking;
    private final AgentLlmService llm;
    private final ObjectMapper objectMapper;
    private final ExecutorService embedExecutor = Executors.newFixedThreadPool(3);

    @Data
    public static class RagFile {
        private String id;
        private String name;
        private String storedName;
        private long size;
        private String type;
        private String strategy;
        private int chunks;
        private String createdAt;
    }

    @Data
    public static class RagDatabase {
        private String id;
        private String name;
        private String description;
        private String createdAt;
        private String updatedAt;
        private List<RagFile> files = new ArrayList<>();
    }

    public record Retrieved(String databaseId, String databaseName, String source,
                            String content, double score) {
    }

    public synchronized List<RagDatabase> listDatabases() {
        List<RagDatabase> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root())) {
            stream.filter(Files::isDirectory).forEach(path -> {
                RagDatabase db = readMeta(path.resolve("meta.json"));
                if (db != null) result.add(db);
            });
        } catch (IOException ex) {
            log.warn("list rag databases failed: {}", ex.getMessage());
        }
        result.sort(Comparator.comparing(RagDatabase::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return result;
    }

    public RagDatabase getDatabase(String id) {
        if (id == null || !id.matches("kb-[a-zA-Z0-9-]{1,40}")) return null;
        return readMeta(root().resolve(id).resolve("meta.json"));
    }

    public synchronized RagDatabase createDatabase(String name, String description) {
        String id = "kb-" + UUID.randomUUID().toString().substring(0, 8);
        RagDatabase db = new RagDatabase();
        db.setId(id);
        db.setName(name == null || name.isBlank() ? "未命名知识库" : name.trim());
        db.setDescription(description == null ? "" : description.trim());
        db.setCreatedAt(Instant.now().toString());
        db.setUpdatedAt(db.getCreatedAt());
        Path dir = root().resolve(id);
        try {
            Files.createDirectories(dir.resolve("files"));
            writeMeta(db);
        } catch (IOException ex) {
            throw new IllegalStateException("创建知识库失败", ex);
        }
        return db;
    }

    public synchronized void deleteDatabase(String id) {
        RagDatabase db = getDatabase(id);
        if (db == null) return;
        redis.clearDatabase(id);
        Path dir = root().resolve(id);
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("删除知识库失败", ex);
        }
    }

    public synchronized RagFile addFile(String databaseId, MultipartFile file,
                                        RagChunkingService.Strategy strategy) {
        RagDatabase db = getDatabase(databaseId);
        if (db == null) throw new IllegalArgumentException("知识库不存在");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件为空");
        if (file.getSize() > 30 * 1024 * 1024) throw new IllegalArgumentException("文件不能超过 30MB");
        String filename = file.getOriginalFilename() == null ? "upload.txt" : file.getOriginalFilename();
        String safeName = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileId = "f-" + UUID.randomUUID().toString().substring(0, 10);
        Path dbDir = root().resolve(databaseId);
        Path fileDir = dbDir.resolve("files").resolve(fileId);
        Path stored = fileDir.resolve(safeName);
        RagFile ragFile = new RagFile();
        ragFile.setId(fileId);
        ragFile.setName(filename);
        ragFile.setStoredName(safeName);
        ragFile.setSize(file.getSize());
        ragFile.setStrategy(strategy.name().toLowerCase(Locale.ROOT));
        ragFile.setCreatedAt(Instant.now().toString());
        try {
            Files.createDirectories(fileDir);
            file.transferTo(stored);
            RagTextExtractor.Extracted extracted = extractor.extract(stored, filename);
            List<String> chunks;
            List<List<Double>> vectors = new ArrayList<>();
            if ("image".equals(extracted.kind())) {
                String mime = file.getContentType();
                chunks = List.of("[图片] " + filename);
                vectors.add(embeddings.embedImage(stored, mime));
            } else {
                chunks = chunking.chunk(extracted.text(), strategy);
                if (chunks.isEmpty()) throw new IllegalArgumentException("文件没有可提取的文本");
                for (String chunk : chunks) {
                    vectors.add(embeddings.embedText(chunk));
                }
            }
            for (int i = 0; i < chunks.size(); i++) {
                String chunkId = "c-" + UUID.randomUUID().toString().substring(0, 12);
                ragFile.setChunks(ragFile.getChunks() + 1);
                redis.addVector(databaseId, chunkId, vectors.get(i));
                redis.saveChunk(databaseId, new RagRedisStore.ChunkMeta(
                        chunkId, fileId, filename, chunks.get(i), i, databaseId));
            }
            ragFile.setChunks(chunks.size());
            db.getFiles().add(ragFile);
            db.setUpdatedAt(Instant.now().toString());
            writeMeta(db);
            log.info("RAG indexed {} chunks for {} into {}", chunks.size(), filename, databaseId);
            return ragFile;
        } catch (Exception ex) {
            cleanupFailedFile(databaseId, ragFile, fileDir);
            throw new IllegalStateException("索引文件失败: " + ex.getMessage(), ex);
        }
    }

    public synchronized void deleteFile(String databaseId, String fileId) {
        if (fileId == null || !fileId.matches("f-[a-zA-Z0-9-]{1,40}")) return;
        RagDatabase db = getDatabase(databaseId);
        if (db == null) return;
        RagFile target = db.getFiles().stream().filter(f -> f.getId().equals(fileId)).findFirst().orElse(null);
        if (target == null) return;
        Set<String> chunks = redis.fileChunks(fileId);
        for (String chunk : chunks) {
            redis.removeVector(databaseId, chunk);
            redis.removeChunkRecords(databaseId, chunk, null);
        }
        redis.removeFileSet(fileId);
        Path dir = root().resolve(databaseId).resolve("files").resolve(fileId);
        deleteDirectory(dir);
        db.getFiles().remove(target);
        db.setUpdatedAt(Instant.now().toString());
        writeMeta(db);
    }

    public List<Retrieved> search(String query, List<String> databaseIds, int finalTopK) {
        if (query == null || query.isBlank()) return List.of();
        if (databaseIds == null || databaseIds.isEmpty()) return List.of();
        List<Double> queryVector = embeddings.embedText(query);
        List<Retrieved> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String databaseId : databaseIds) {
            RagDatabase db = getDatabase(databaseId);
            if (db == null) continue;
            List<RagRedisStore.Hit> hits = redis.search(databaseId, queryVector,
                    Math.max(4, properties.getCoarseTopK()));
            for (RagRedisStore.Hit hit : hits) {
                if (!seen.add(databaseId + ":" + hit.chunkId())) continue;
                RagRedisStore.ChunkMeta meta = redis.getChunk(databaseId, hit.chunkId());
                if (meta != null) {
                    candidates.add(new Retrieved(databaseId, db.getName(), meta.source(),
                            meta.content(), hit.score()));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Retrieved::score).reversed());
        if (candidates.isEmpty()) return List.of();
        int limit = Math.max(1, finalTopK <= 0 ? properties.getFinalTopK() : finalTopK);
        List<Retrieved> selected = rerank(query, candidates, limit);
        return selected.isEmpty() ? candidates.subList(0, Math.min(limit, candidates.size())) : selected;
    }

    /**
     * Agentic RAG: a separate clean-context LLM call only sees the query and
     * candidate chunks, never the main agent's thoughts or tool trace.
     */
    private List<Retrieved> rerank(String query, List<Retrieved> candidates, int limit) {
        if (!llm.isConfigured()) return candidates.subList(0, Math.min(limit, candidates.size()));
        int size = Math.min(candidates.size(), 18);
        StringBuilder user = new StringBuilder("用户问题：\n").append(query).append("\n\n候选片段：\n");
        for (int i = 0; i < size; i++) {
            Retrieved item = candidates.get(i);
            user.append("[").append(i).append("] 来源：").append(item.source())
                    .append("\n").append(clip(item.content(), 900)).append("\n\n");
        }
        user.append("""
                请只保留对回答当前问题真正有帮助的片段，删除重复、离题、信息量低的内容。
                最多保留 %d 条。只输出 JSON：{"selected":[0,2],"reason":"一句理由"}。
                """.formatted(limit));
        String system = "你是独立的 RAG 重排器。你只根据问题与候选片段判断相关性，不知道主 Agent 的上下文或计划。";
        try {
            JsonNode json = llm.completeJson(system, user.toString(), 60);
            JsonNode selected = json.path("selected");
            List<Retrieved> result = new ArrayList<>();
            if (selected.isArray()) {
                for (JsonNode index : selected) {
                    int i = index.asInt(-1);
                    if (i >= 0 && i < size && result.size() < limit) result.add(candidates.get(i));
                }
            }
            return result;
        } catch (Exception ex) {
            log.warn("RAG rerank failed, use vector order: {}", ex.getMessage());
            return candidates.subList(0, Math.min(limit, candidates.size()));
        }
    }

    public String formatForAgent(List<Retrieved> chunks) {
        if (chunks.isEmpty()) return "没有检索到相关知识库片段。";
        StringBuilder sb = new StringBuilder("知识库检索结果（已由独立重排 Agent 过滤）：\n");
        for (int i = 0; i < chunks.size(); i++) {
            Retrieved item = chunks.get(i);
            sb.append("\n[").append(i + 1).append("] 知识库：").append(item.databaseName())
                    .append("；来源：").append(item.source()).append("\n")
                    .append(item.content()).append("\n");
        }
        return sb.toString();
    }

    private void cleanupFailedFile(String databaseId, RagFile file, Path fileDir) {
        try {
            Set<String> chunks = redis.fileChunks(file.getId());
            for (String chunk : chunks) {
                redis.removeVector(databaseId, chunk);
                redis.removeChunkRecords(databaseId, chunk, null);
            }
            redis.removeFileSet(file.getId());
        } catch (Exception ignored) {
        }
        deleteDirectory(fileDir);
    }

    private void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private RagDatabase readMeta(Path file) {
        if (!Files.exists(file)) return null;
        try {
            return objectMapper.readValue(file.toFile(), RagDatabase.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private void writeMeta(RagDatabase db) {
        Path dir = root().resolve(db.getId());
        try {
            Files.createDirectories(dir.resolve("files"));
            objectMapper.writeValue(dir.resolve("meta.json").toFile(), db);
        } catch (IOException ex) {
            throw new IllegalStateException("保存知识库元数据失败", ex);
        }
    }

    public Path root() {
        Path root = Path.of(properties.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("创建 RAG 目录失败", ex);
        }
        return root;
    }

    private String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
