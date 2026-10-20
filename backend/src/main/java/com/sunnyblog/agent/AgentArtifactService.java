package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
                    .append(artifact.type()).append(", ").append(artifact.size()).append(" bytes)");
            String detail = inspectSummary(artifact.path());
            if (!detail.isBlank()) sb.append("：").append(detail);
            sb.append("\n");
        }
        return sb.toString();
    }

    public String inspect(String relative) {
        if (relative != null && relative.startsWith("artifacts/")) relative = relative.substring("artifacts/".length());
        Path path = resolve(relative);
        if (!Files.isRegularFile(path)) return "产物不存在: " + relative;
        try {
            String type = typeOf(relative);
            String summary = inspectSummary(relative);
            return "产物: " + relative + "\n类型: " + type + "\n大小: " + Files.size(path)
                    + " bytes\n" + (summary.isBlank() ? "无额外结构信息" : summary);
        } catch (IOException ex) {
            return "无法读取产物: " + ex.getMessage();
        }
    }

    private String inspectSummary(String relative) {
        Path path = resolve(relative);
        String type = typeOf(relative);
        try {
            if (type.equals("pptx")) {
                int slides = 0;
                StringBuilder text = new StringBuilder();
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.getName().matches("ppt/slides/slide\\d+\\.xml")) {
                            slides++;
                            text.append(extractText(readEntry(zip, entry), "a:t")).append(' ');
                        }
                    }
                }
                return "幻灯片页数=" + slides + "；文本=" + clip(text.toString(), 600);
            }
            if (type.equals("docx")) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    ZipEntry entry = zip.getEntry("word/document.xml");
                    if (entry == null) return "Word 文档中没有 document.xml";
                    String text = extractText(readEntry(zip, entry), "w:t");
                    return "文本=" + clip(text, 700);
                }
            }
            if (type.equals("xlsx")) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    long sheets = zip.stream().filter(e -> e.getName().matches("xl/worksheets/sheet\\d+\\.xml")).count();
                    return "工作表数量=" + sheets;
                }
            }
            if (type.equals("image")) {
                BufferedImage image = ImageIO.read(path.toFile());
                return image == null ? "图片无法解析" : "图片尺寸=" + image.getWidth() + "x" + image.getHeight();
            }
            if (type.equals("html") || type.equals("markdown") || type.equals("text")
                    || type.equals("json") || type.equals("csv")) {
                return "内容预览=" + clip(Files.readString(path, StandardCharsets.UTF_8), 500);
            }
            return "";
        } catch (Exception ex) {
            return "结构解析失败: " + ex.getMessage();
        }
    }

    private String extractText(byte[] xmlBytes, String tag) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("<" + tag + "[^>]*>(.*?)</" + tag + ">", Pattern.DOTALL).matcher(xml);
        StringBuilder text = new StringBuilder();
        while (matcher.find()) {
            String value = matcher.group(1).replaceAll("<[^>]+>", "").trim();
            if (!value.isBlank()) text.append(value).append(' ');
        }
        return text.toString().trim();
    }

    private byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private String clip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
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
