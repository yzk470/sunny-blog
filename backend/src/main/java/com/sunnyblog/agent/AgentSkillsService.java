package com.sunnyblog.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillsService {

    private final AgentProperties properties;

    public record Skill(String slug, String name, String description, String body) {}

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
        try (Stream<Path> stream = Files.walk(skillsRoot(), 4)) {
            stream.filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> parse(path).ifPresent(result::add));
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
            return new Skill(safeSlug, safeName, description == null ? "" : description, body);
        } catch (IOException ex) {
            throw new IllegalStateException("保存技能失败: " + ex.getMessage(), ex);
        }
    }

    public void delete(String slug) {
        Optional<Skill> skill = find(slug);
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
            return Optional.of(new Skill(slug, name, description, body));
        } catch (IOException ex) {
            log.warn("解析技能失败 {}: {}", path, ex.getMessage());
            return Optional.empty();
        }
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
