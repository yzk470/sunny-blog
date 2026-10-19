package com.sunnyblog.werewolf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Writes the AI players' daytime speeches with the same DeepSeek ChatClient
 * already used by the digital human. Decisions stay rule-based so a game never
 * stalls on model latency; only the spoken table talk needs the LLM.
 */
@Slf4j
@Service
public class WerewolfAiService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final boolean configured;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public WerewolfAiService(ObjectProvider<ChatClient.Builder> builderProvider,
                             ObjectMapper objectMapper,
                             @Value("${spring.ai.openai.api-key:}") String apiKey) {
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
        this.objectMapper = objectMapper;
        this.configured = chatClient != null && apiKey != null && !apiKey.isBlank();
        if (configured) {
            log.info("Werewolf AI speeches enabled with the configured DeepSeek/OpenAI model");
        } else {
            log.warn("Werewolf AI speeches use local fallback: no model API key is configured");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Generate one line for every living AI player in one model round-trip.
     * Keeping this to a single call keeps a discussion round responsive.
     */
    public Map<String, String> generateSpeeches(WerewolfGame game) {
        if (!configured) return Map.of();
        WerewolfGame.GameView view = game.toView();
        String aiRoles = game.aliveAiPlayers().stream()
                .map(p -> p.getId() + " " + p.getName() + "（" + p.getRole().getLabel() + "）")
                .collect(Collectors.joining("\n"));
        String publicLog = view.logs().stream()
                .filter(l -> !"role".equals(l.type()) && !"private".equals(l.type()))
                .skip(Math.max(0, view.logs().size() - 24L))
                .map(l -> "[" + l.phaseLabel() + "] " + l.text())
                .collect(Collectors.joining("\n"));

        String system = """
                你是狼人杀游戏的多角色编导，正在为一场中文狼人杀写白天发言。
                你要理解每个 AI 玩家的秘密身份，但写出来的发言不能直接暴露狼人身份。
                规则：
                1. 狼人必须伪装成好人，可以甩锅、跟票或轻踩一个目标。
                2. 村民站在好人视角分析怀疑对象。
                3. 预言家如果已经公开身份，可以报告查验；否则可以暗示但不直接报身份。
                4. 女巫、猎人不主动暴露身份。
                5. 每句 25～55 个中文字符，口语化，有个性，不要重复同一句话。
                6. 发言之间可以互相点名、质疑，但不要写出玩家 ID。
                只输出一个 JSON 对象，键是玩家 ID，值是发言。不要 markdown 代码块，不要解释。
                """;

        String user = "当前是第 " + view.round() + " 轮白天，存活玩家：\n"
                + game.allPlayers().stream()
                    .filter(WerewolfGame.Player::isAlive)
                    .map(p -> "- " + p.getName() + (p.isHuman() ? "（人类玩家）" : ""))
                    .collect(Collectors.joining("\n"))
                + "\n\n存活 AI 的秘密身份：\n" + aiRoles
                + "\n\n近期公开事件：\n" + publicLog
                + "\n\n请输出 JSON。";

        try {
            String raw = CompletableFuture.supplyAsync(() -> chatClient.prompt()
                            .system(system)
                            .user(user)
                            .call()
                            .content(), executor)
                    .get(22, TimeUnit.SECONDS);
            Map<String, String> result = parse(raw);
            if (!result.isEmpty()) {
                log.info("Werewolf AI generated {} speeches", result.size());
            }
            return result;
        } catch (Exception ex) {
            log.warn("Werewolf AI speech generation failed; using fallback: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, String> parse(String raw) {
        if (raw == null) return Map.of();
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) text = text.substring(firstNewline + 1);
            if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) text = text.substring(start, end + 1);
        try {
            JsonNode root = objectMapper.readTree(text);
            Map<String, String> result = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual()) {
                    String value = entry.getValue().asText().trim();
                    if (!value.isEmpty()) result.put(entry.getKey(), value);
                }
            });
            return result;
        } catch (Exception ex) {
            log.debug("Could not parse werewolf speech JSON: {}", text);
            return Map.of();
        }
    }
}
