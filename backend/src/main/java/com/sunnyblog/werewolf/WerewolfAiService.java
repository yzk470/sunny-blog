package com.sunnyblog.werewolf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * DeepSeek brain used by every AI decision and every AI speech.
 * Each call receives only the private context of the acting agent.
 */
@Slf4j
@Service
public class WerewolfAiService implements WerewolfGame.DecisionProvider {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final boolean configured;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public WerewolfAiService(ObjectProvider<ChatClient.Builder> builderProvider,
                             ObjectMapper objectMapper,
                             @Value("${spring.ai.openai.api-key:}") String apiKey,
                             @Value("${app.werewolf.ai-enabled:true}") boolean aiEnabled) {
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
        this.objectMapper = objectMapper;
        this.configured = aiEnabled && chatClient != null && apiKey != null && !apiKey.isBlank();
        if (configured) {
            log.info("Werewolf AI decisions and streaming speeches enabled with the configured DeepSeek model");
        } else {
            log.warn("Werewolf AI uses fallback: no model API key is configured");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    // ─────────────────────────────────────────────────────────────
    // Independent decision contexts
    // ─────────────────────────────────────────────────────────────

    @Override
    public String chooseWolfTarget(WerewolfGame game) {
        WerewolfGame.Player wolf = game.aliveWerewolvesView().stream()
                .filter(p -> !p.isHuman()).findFirst().orElse(null);
        if (!configured || wolf == null) return null;
        String candidates = game.aliveGoodView().stream()
                .map(p -> "- " + p.getName() + "(" + p.getId() + ")")
                .collect(Collectors.joining("\n"));
        String system = roleSystem(game, wolf, """
                你是狼人。目标必须是存活好人，不能是狼队友。
                考虑谁最可能威胁狼队、谁像预言家/女巫/猎人、谁怀疑过你们，以及白天票型。
                输出 JSON：{"targetId":"pX","reason":"一句中文理由"}
                """);
        String user = publicUser(game, "可袭击的好人：\n" + candidates + "\n\n请决定今晚杀谁，输出 JSON。");
        JsonNode json = callJson(system, user, 40);
        String target = resolveId(game, textOrNull(json, "targetId"));
        log.info("Werewolf AI wolf target: {} ({})", target, textOrNull(json, "reason"));
        return target;
    }

    @Override
    public String chooseSeerCheck(WerewolfGame game, WerewolfGame.Player seer) {
        if (!configured || seer == null) return null;
        String candidates = game.alivePlayersView().stream()
                .filter(p -> !p.getId().equals(seer.getId()))
                .map(p -> "- " + p.getName() + "(" + p.getId() + ")")
                .collect(Collectors.joining("\n"));
        String system = roleSystem(game, seer, """
                你是预言家。优先查验发言矛盾、带节奏、被多人怀疑、尚未查验的玩家。
                输出 JSON：{"targetId":"pX","reason":"一句中文理由"}
                """);
        String user = publicUser(game, "可查验玩家：\n" + candidates + "\n\n请决定今晚查验谁，输出 JSON。");
        JsonNode json = callJson(system, user, 40);
        String target = resolveId(game, textOrNull(json, "targetId"));
        log.info("Werewolf AI seer check: {} ({})", target, textOrNull(json, "reason"));
        return target;
    }

    @Override
    public Boolean chooseWitchSave(WerewolfGame game, WerewolfGame.Player witch, WerewolfGame.Player victim) {
        if (!configured || witch == null || victim == null) return null;
        String system = roleSystem(game, witch, """
                你是女巫。只有你知道谁被狼人袭击。判断是否值得用掉唯一的解药：
                考虑首夜、死者是否像神职、是否可能是狼人自刀、后续局势。
                输出 JSON：{"save":true/false,"reason":"一句中文理由"}
                """);
        String user = publicUser(game, "今晚被袭击的人：" + victim.getName() + "(" + victim.getId()
                + ")\n\n是否使用解药？输出 JSON。");
        JsonNode json = callJson(system, user, 35);
        if (json == null || json.get("save") == null || json.get("save").isNull()) return null;
        boolean save = json.get("save").asBoolean();
        log.info("Werewolf AI witch save={} ({})", save, textOrNull(json, "reason"));
        return save;
    }

    @Override
    public String chooseWitchPoison(WerewolfGame game, WerewolfGame.Player witch) {
        if (!configured || witch == null) return null;
        String candidates = game.alivePlayersView().stream()
                .filter(p -> !p.getId().equals(witch.getId()))
                .map(p -> "- " + p.getName() + "(" + p.getId() + ")")
                .collect(Collectors.joining("\n"));
        String system = roleSystem(game, witch, """
                你是女巫。判断是否使用唯一的毒药，以及毒谁。
                优先考虑公开逻辑里最像狼人的人，避免毒明显好人。不想用毒时 targetId 输出 null。
                输出 JSON：{"targetId":"pX 或 null","reason":"一句中文理由"}
                """);
        String user = publicUser(game, "可毒玩家：\n" + candidates + "\n\n请决定是否用毒，输出 JSON。");
        JsonNode json = callJson(system, user, 35);
        String target = resolveId(game, textOrNull(json, "targetId"));
        log.info("Werewolf AI witch poison: {} ({})", target, textOrNull(json, "reason"));
        return target;
    }

    @Override
    public Map<String, String> chooseVotes(WerewolfGame game, List<WerewolfGame.Player> voters) {
        if (!configured || voters == null || voters.isEmpty()) return Map.of();
        List<CompletableFuture<Map.Entry<String, String>>> futures = new ArrayList<>();
        for (WerewolfGame.Player voter : voters) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                String target = chooseVoteFor(game, voter);
                return target == null ? null : Map.entry(voter.getId(), target);
            }, executor));
        }
        Map<String, String> votes = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, String>> future : futures) {
            try {
                Map.Entry<String, String> entry = future.get(45, TimeUnit.SECONDS);
                if (entry != null) votes.put(entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                log.warn("Werewolf AI vote failed; engine will use a fallback: {}", ex.getMessage());
            }
        }
        log.info("Werewolf AI votes: {}", votes);
        return votes;
    }

    private String chooseVoteFor(WerewolfGame game, WerewolfGame.Player voter) {
        String candidates = game.alivePlayersView().stream()
                .filter(p -> !p.getId().equals(voter.getId()))
                .map(p -> "- " + p.getName() + "(" + p.getId() + ")")
                .collect(Collectors.joining("\n"));
        String system = roleSystem(game, voter, """
                现在要投放逐票。狼人隐藏身份并投掉威胁最大的好人，不能投狼队友；
                好人投最可疑的人。只输出 JSON：{"targetId":"pX","reason":"一句中文理由"}
                """);
        String user = publicUser(game, "可投票玩家：\n" + candidates + "\n\n请决定投谁，输出 JSON。");
        JsonNode json = callJson(system, user, 40);
        return resolveId(game, textOrNull(json, "targetId"));
    }

    @Override
    public String chooseHunterShot(WerewolfGame game, WerewolfGame.Player hunter) {
        if (!configured || hunter == null) return null;
        String candidates = game.alivePlayersView().stream()
                .filter(p -> !p.getId().equals(hunter.getId()))
                .map(p -> "- " + p.getName() + "(" + p.getId() + ")")
                .collect(Collectors.joining("\n"));
        String system = roleSystem(game, hunter, """
                你是猎人，刚刚出局，可以开枪带走一名存活玩家。选择最像狼人的目标。
                只输出 JSON：{"targetId":"pX","reason":"一句中文理由"}
                """);
        String user = publicUser(game, "可开枪目标：\n" + candidates + "\n\n请决定带谁，输出 JSON。");
        JsonNode json = callJson(system, user, 40);
        String target = resolveId(game, textOrNull(json, "targetId"));
        log.info("Werewolf AI hunter shot: {} ({})", target, textOrNull(json, "reason"));
        return target;
    }

    // ─────────────────────────────────────────────────────────────
    // Live speech streaming
    // ─────────────────────────────────────────────────────────────

    /**
     * Stream one agent's speech token by token to the SSE consumer.
     * The prompt contains only this agent's role knowledge plus public history.
     */
    public String streamSpeech(WerewolfGame game, WerewolfGame.Player speaker,
                               String fallback, Consumer<String> tokenSink) {
        if (!configured) {
            streamFallback(fallback, tokenSink);
            return fallback;
        }
        String system = roleSystem(game, speaker, """
                现在轮到你发言。
                - 村民：分析怀疑对象和票型。
                - 狼人：伪装成好人，可以甩锅/跟票/轻踩，但绝不能承认自己是狼。
                - 预言家：可以暗示或公开查验，但要按你自己的判断。
                - 女巫/猎人：不要主动把身份说死。
                说 30～70 个中文字符，口语化，像 QQ 群里真人聊天。不要写玩家 ID，不要旁白。
                """);
        String user = publicUser(game, "请直接输出你要说的一句话。");
        StringBuilder full = new StringBuilder();
        try {
            chatClient.prompt()
                    .system(system)
                    .user(user)
                    .stream()
                    .content()
                    .timeout(Duration.ofSeconds(45))
                    .doOnNext(token -> {
                        full.append(token);
                        tokenSink.accept(token);
                    })
                    .blockLast();
        } catch (Exception ex) {
            log.warn("Werewolf AI speech stream failed for {}: {}", speaker.getName(), ex.getMessage());
        }
        String text = full.toString().trim();
        if (text.isEmpty()) {
            streamFallback(fallback, tokenSink);
            return fallback;
        }
        return text;
    }

    private void streamFallback(String fallback, Consumer<String> tokenSink) {
        if (fallback == null || fallback.isBlank()) return;
        for (int i = 0; i < fallback.length(); i += 2) {
            tokenSink.accept(fallback.substring(i, Math.min(fallback.length(), i + 2)));
            try {
                Thread.sleep(18);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private String roleSystem(WerewolfGame game, WerewolfGame.Player player, String instructions) {
        return "你在进行一局中文狼人杀。\n"
                + game.privateKnowledge(player)
                + "你只能知道自己身份允许知道的信息；不知道其他玩家的秘密身份，除非规则公开。\n"
                + instructions
                + "\n只输出要求的格式，不要 markdown，不要解释。";
    }

    private String publicUser(WerewolfGame game, String tail) {
        return "公开事件记录（从开局到现在的追加历史）：\n"
                + game.publicTranscript()
                + "\n\n当前存活玩家：\n"
                + game.alivePlayersView().stream()
                    .map(p -> "- " + p.getName() + "(" + p.getId() + ")"
                            + (p.isHuman() ? " [人类]" : "")
                            + (p.getClaimedRole() == null ? "" : " [自称" + p.getClaimedRole() + "]"))
                    .collect(Collectors.joining("\n"))
                + "\n\n" + tail;
    }

    private JsonNode callJson(String system, String user, int timeoutSeconds) {        try {
            String raw = CompletableFuture.supplyAsync(() -> chatClient.prompt()
                            .system(system)
                            .user(user)
                            .call()
                            .content(), executor)
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            JsonNode node = parseJson(raw);
            if (node == null) throw new IllegalStateException("模型没有返回有效 JSON");
            return node;
        } catch (Exception ex) {
            log.warn("Werewolf AI model call failed: {}", ex.getMessage());
            throw new IllegalStateException("模型决策失败", ex);
        }
    }

    private JsonNode parseJson(String raw) {
        if (raw == null) return null;
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
            return objectMapper.readTree(text);
        } catch (Exception ex) {
            log.debug("Could not parse werewolf model JSON: {}", text);
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText().trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private String resolveId(WerewolfGame game, String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) return null;
        for (WerewolfGame.Player p : game.allPlayers()) {
            if (value.equals(p.getId())) return p.getId();
        }
        for (WerewolfGame.Player p : game.allPlayers()) {
            if (value.equals(p.getName())) return p.getId();
        }
        for (WerewolfGame.Player p : game.allPlayers()) {
            if (value.contains(p.getId()) || value.contains(p.getName())) return p.getId();
        }
        return null;
    }
}
