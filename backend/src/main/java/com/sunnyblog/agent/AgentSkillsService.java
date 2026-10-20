package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillsService {

    private final AgentProperties properties;

    public record Skill(String slug, String name, String description, String body, List<String> resources) {}

    public Path skillsRoot() {
        Path root = Path.of(properties.getDir()).toAbsolutePath().normalize().resolve("skills");
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建 skills 目录", ex);
        }
        return root;
    }

    public List<Skill> list() {
        List<Skill> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(skillsRoot(), 5)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> parse(path).ifPresent(result::add));
            // A minimal skill can also be a single Markdown file at the skills root.
            try (Stream<Path> loose = Files.list(skillsRoot())) {
                loose.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(path -> parseLoose(path).ifPresent(result::add));
            }
        } catch (IOException ex) {
            log.warn("读取 skills 失败: {}", ex.getMessage());
        }
        return result;
    }

    public Optional<Skill> find(String slugOrName) {
        if (slugOrName == null) return Optional.empty();
        String needle = slugOrName.trim();
        return list().stream()
                .filter(s -> s.slug().equalsIgnoreCase(needle) || s.name().equalsIgnoreCase(needle))
                .findFirst();
    }

    public Skill save(String slug, String name, String description, String content) {
        String safeName = (name == null || name.isBlank()) ? "未命名技能" : name.trim();
        String safeSlug = slugify((slug == null || slug.isBlank()) ? safeName : slug);
        Path dir = skillsRoot().resolve(safeSlug).normalize();
        ensureInside(dir, skillsRoot());
        try {
            Files.createDirectories(dir);
            String body = content == null ? "" : content.trim();
            String markdown = "---\nname: " + safeName.replace("\n", " ")
                    + "\ndescription: " + (description == null ? "" : description.replace("\n", " "))
                    + "\n---\n\n" + body + "\n";
            Files.writeString(dir.resolve("SKILL.md"), markdown, StandardCharsets.UTF_8);
            log.info("Saved agent skill {} at {}", safeName, dir);
            return new Skill(safeSlug, safeName, description == null ? "" : description, body,
                    List.of("SKILL.md"));
        } catch (IOException ex) {
            throw new IllegalStateException("保存技能失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * Imports a deepagents-style skill directory zipped by the admin: SKILL.md,
     * reference markdown, scripts and assets are all preserved.
     */
    public Skill importZip(byte[] data, String fallbackName) {
        if (data == null || data.length == 0) throw new IllegalArgumentException("zip 文件为空");
        if (data.length > 30 * 1024 * 1024) throw new IllegalArgumentException("zip 文件不能超过 30MB");
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.isBlank() || name.startsWith("__MACOSX/") || name.contains("/.")) continue;
                if (name.contains("__pycache__") || name.endsWith(".pyc")) continue;
                Path normalized = Path.of(name).normalize();
                if (normalized.isAbsolute() || normalized.startsWith("..")) {
                    throw new IllegalArgumentException("zip 中包含非法路径");
                }
                byte[] bytes = zip.readAllBytes();
                if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("单个技能文件不能超过 2MB");
                files.put(normalized.toString().replace('\\', '/'), bytes);
                if (files.size() > 400) throw new IllegalArgumentException("技能文件数量过多");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("解析 zip 失败: " + ex.getMessage(), ex);
        }
        if (files.isEmpty()) throw new IllegalArgumentException("zip 中没有文件");
        if (files.keySet().stream().noneMatch(p -> p.equalsIgnoreCase("SKILL.md") || p.endsWith("/SKILL.md"))) {
            throw new IllegalArgumentException("zip 中缺少 SKILL.md");
        }
        // If every entry lives under one top-level folder, strip it to avoid
        // skills/pptx/pptx/SKILL.md nesting.
        String common = commonPrefix(files.keySet());
        String slug = slugify((common == null || common.isBlank()) ? fallbackName : common);
        Path target = skillsRoot().resolve(slug).normalize();
        ensureInside(target, skillsRoot());
        try {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                String relative = file.getKey();
                final String entryName = relative;
                if (files.keySet().stream().anyMatch(k -> k.startsWith(entryName + "/"))) {
                    // Compress-Archive sometimes emits a directory entry without
                    // a trailing slash; it must not overwrite a real directory.
                    continue;
                }
                if (common != null && !common.isBlank() && relative.startsWith(common + "/")) {
                    relative = relative.substring(common.length() + 1);
                }
                Path dest = target.resolve(relative).normalize();
                ensureInside(dest, skillsRoot());
                Files.createDirectories(dest.getParent());
                Files.write(dest, file.getValue());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("导入技能目录失败: " + ex.getMessage(), ex);
        }
        return list().stream()
                .filter(s -> s.slug().equalsIgnoreCase(slug) || s.name().equalsIgnoreCase(slug))
                .findFirst()
                .orElseGet(() -> new Skill(slug, slug, "从 zip 导入", "", List.of("SKILL.md")));
    }

    private String commonPrefix(java.util.Set<String> paths) {
        String common = null;
        for (String path : paths) {
            if (!path.contains("/")) return null;
            String first = path.substring(0, path.indexOf('/'));
            if (common == null) common = first;
            else if (!common.equals(first)) return null;
        }
        return common;
    }

    public void delete(String slug) {        Optional<Skill> skill = find(slug);
        if (skill.isEmpty()) return;
        Path dir = skillsRoot().resolve(skill.get().slug()).normalize();
        ensureInside(dir, skillsRoot());
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("删除技能失败: " + ex.getMessage(), ex);
        }
    }

    public String catalog() {
        List<Skill> skills = list();
        if (skills.isEmpty()) return "（还没有导入技能）";
        StringBuilder sb = new StringBuilder();
        for (Skill skill : skills) {
            sb.append("- ").append(skill.name()).append(" [").append(skill.slug()).append("]：")
                    .append(skill.description().isBlank() ? "无描述" : skill.description()).append("\n");
        }
        return sb.toString();
    }

    private Optional<Skill> parse(Path path) {
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            String name = path.getParent().getFileName().toString();
            String description = "";
            String body = raw;
            if (raw.startsWith("---")) {
                int end = raw.indexOf("\n---", 3);
                if (end > 0) {
                    String front = raw.substring(3, end);
                    for (String line : front.split("\n")) {
                        int colon = line.indexOf(':');
                        if (colon <= 0) continue;
                        String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                        String value = line.substring(colon + 1).trim();
                        if (key.equals("name") && !value.isBlank()) name = value;
                        if (key.equals("description")) description = value;
                    }
                    body = raw.substring(end + 4).trim();
                }
            }
            String slug = path.getParent().getFileName().toString();
            return Optional.of(new Skill(slug, name, description, body, resources(path.getParent())));
        } catch (IOException ex) {
            log.warn("解析技能失败 {}: {}", path, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Skill> parseLoose(Path path) {
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            String name = path.getFileName().toString().replaceAll("\\.md$", "");
            String description = "";
            String body = raw;
            if (raw.startsWith("---")) {
                int end = raw.indexOf("\n---", 3);
                if (end > 0) {
                    for (String line : raw.substring(3, end).split("\n")) {
                        int colon = line.indexOf(':');
                        if (colon <= 0) continue;
                        String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                        String value = line.substring(colon + 1).trim();
                        if (key.equals("name") && !value.isBlank()) name = value;
                        if (key.equals("description")) description = value;
                    }
                    body = raw.substring(end + 4).trim();
                }
            }
            String slug = path.getFileName().toString().replaceAll("\\.md$", "");
            return Optional.of(new Skill(slug, name, description, body, List.of(path.getFileName().toString())));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private List<String> resources(Path directory) {
        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory, 5)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .filter(p -> !p.toString().contains("__pycache__"))
                    .filter(p -> !p.getFileName().toString().endsWith(".pyc"))
                    .map(p -> directory.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .limit(120)
                    .forEach(files::add);
        } catch (IOException ignored) {
        }
        return files;
    }

    private String slugify(String input) {
        String slug = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) slug = "skill-" + UUID.randomUUID().toString().substring(0, 8);
        return slug;
    }

    private void ensureInside(Path path, Path root) {
        if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("非法技能路径");
        }
    }
}
