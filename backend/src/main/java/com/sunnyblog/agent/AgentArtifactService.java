package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AgentArtifactService {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public record Artifact(String name, String path, String type, long size, String url) {
    }

    public Path root() {
        Path path = Path.of(properties.getDir()).toAbsolutePath().normalize().resolve("artifacts");
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建 artifacts 目录", ex);
        }
        return path;
    }

    public List<Artifact> listSince(Instant since) {
        return list().stream()
                .filter(a -> {
                    try {
                        Path file = resolve(a.path());
                        return !Files.getLastModifiedTime(file).toInstant().isBefore(since.minusSeconds(2));
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .toList();
    }

    public List<Artifact> list() {
        List<Artifact> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root(), 8)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing((Path p) -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException ex) {
                            return 0L;
                        }
                    }).reversed())
                    .limit(200)
                    .forEach(path -> {
                        try {
                            String relative = root().relativize(path).toString().replace('\\', '/');
                            result.add(new Artifact(
                                    path.getFileName().toString(),
                                    relative,
                                    typeOf(relative),
                                    Files.size(path),
                                    "/api/agent/artifacts?path=" + urlEncode(relative)
                            ));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
        return result;
    }

    public String manifestSince(Instant since) {
        List<Artifact> artifacts = listSince(since);
        if (artifacts.isEmpty()) return "本轮没有新增产物文件。";
        StringBuilder sb = new StringBuilder("本轮新增产物：\n");
        for (Artifact artifact : artifacts) {
            sb.append("- ").append(artifact.path()).append(" (")
                    .append(artifact.type()).append(", ").append(artifact.size()).append(" bytes)\n");
        }
        return sb.toString();
    }

    public Artifact describe(String relative) {
        Path path = resolve(relative);
        if (!Files.isRegularFile(path)) return null;
        try {
            return new Artifact(path.getFileName().toString(), relative, typeOf(relative),
                    Files.size(path), "/api/agent/artifacts?path=" + urlEncode(relative));
        } catch (IOException ex) {
            return null;
        }
    }

    public Path resolve(String relative) {
        if (relative == null || relative.isBlank()) throw new IllegalArgumentException("产物路径为空");
        Path path = root().resolve(relative).normalize();
        if (!path.startsWith(root())) throw new IllegalArgumentException("非法产物路径");
        return path;
    }

    public String typeOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pptx")) return "pptx";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".xlsx")) return "xlsx";
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".png")) return "image";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) return "image";
        if (lower.endsWith(".svg")) return "image";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".txt")) return "text";
        return "file";
    }

    public String json(List<Artifact> artifacts) {
        try {
            return objectMapper.writeValueAsString(artifacts);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }
}
