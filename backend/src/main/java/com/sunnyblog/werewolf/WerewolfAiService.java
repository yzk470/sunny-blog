package com.sunnyblog.werewolf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * DeepSeek-backed decision brain for every AI-controlled action: wolf kill,
 * witch potions, seer check, votes, hunter shot and daytime speeches.
 * The engine validates every result before applying it; if the model is
 * unreachable a safe local fallback prevents the table from freezing.
 */
@Slf4j
@Service
public class WerewolfAiService implements WerewolfGame.DecisionProvider {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final boolean configured;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public WerewolfAiService(ObjectProvider<ChatClient.Builder> builderProvider,
                             ObjectMapper objectMapper,
                             @Value("${spring.ai.openai.api-key:}") String apiKey) {
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
        this.objectMapper = objectMapper;
        this.configured = chatClient != null && apiKey != null && !apiKey.isBlank();
        if (configured) {
            log.info("Werewolf AI decisions and speeches enabled with the configured DeepSeek/OpenAI model");
        } else {
            log.warn("Werewolf AI uses local fallback: no model API key is configured");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    // ─────────────────────────────────────────────────────────────
    // Night decisions
    // ─────────────────────────────────────────────────────────────

    @Override
    public WerewolfGame.NightPlan chooseNight(WerewolfGame game) {
        if (!configured) return null;
        List<WerewolfGame.Player> aiWolves = game.aliveWerewolvesView().stream()
                .filter(p -> !p.isHuman()).toList();
        WerewolfGame.Player witch = game.alivePlayersView().stream()
                .filter(p -> p.getRole() == WerewolfGame.Role.WITCH && !p.isHuman())
                .findFirst().orElse(null);
        WerewolfGame.Player seer = game.alivePlayersView().stream()
                .filter(p -> p.getRole() == WerewolfGame.Role.SEER && !p.isHuman())
                .findFirst().orElse(null);

        String aiWolfInfo = aiWolves.isEmpty() ? "无（狼人由人类玩家控制）"
                : aiWolves.stream().map(p -> p.getName() + "(" + p.getId() + ")").collect(Collectors.joining("、"));
        String witchInfo = witch == null ? "无（女巫由人类玩家控制或已出局）"
                : witch.getName() + "(" + witch.getId() + ")，解药:" + (witch.isAlive() && hasAntidote(witch) ? "有" : "无")
                + "，毒药:" + (witch.isAlive() && hasPoison(witch) ? "有" : "无");
        String seerInfo = seer == null ? "无（预言家由人类玩家控制或已出局）"
                : seer.getName() + "(" + seer.getId() + ")，已查验: " + checksText(game, seer);

        String goodCandidates = game.aliveGoodView().stream()
                .map(p -> p.getName() + "(" + p.getId() + ")")
                .collect(Collectors.joining("、"));
        String alive = game.alivePlayersView().stream()
                .map(p -> "- " + p.getName() + "(" + p.getId() + ")"
                        + (p.isHuman() ? " [人类]" : " [" + p.getRole().getLabel() + "]"))
                .collect(Collectors.joining("\n"));
        String knownVictim = game.currentWolfTarget() == null ? "尚未确定"
                : nameOf(game, game.currentWolfTarget());

        String system = """
                你是狼人杀桌游的 AI 决策核心，要为所有 AI 玩家做出最优夜间选择。
                你不是随机机器人，必须结合身份、公开信息、投票历史、怀疑度和长期收益判断。
                规则约束：
                1. 狼人只能袭击存活好人，不能袭击狼队友。
                2. 预言家只能查验存活的其他玩家，优先查验高价值目标。
                3. 女巫解药和毒药各一次，同一晚只能使用一瓶；解药只能救今晚被狼人袭击的人。
                4. 女巫可以选择不用毒药；能查杀的狼人优先。
                5. 目标必须使用玩家 ID（例如 p3）。
                只输出 JSON，不要 markdown，不要解释：
                {"wolfTarget":"pX 或 null","witchSave":true/false/null,"witchPoison":"pY 或 null","seerCheck":"pZ 或 null"}
                """;

        String user = "当前第 " + game.getRound() + " 夜。存活玩家：\n" + alive
                + "\n\nAI 狼队：" + aiWolfInfo
                + "\nAI 女巫：" + witchInfo
                + "\nAI 预言家：" + seerInfo
                + "\n今晚已知的狼人袭击目标：" + knownVictim
                + "\n可袭击好人：" + goodCandidates
                + "\n\n近期公开事件：\n" + game.publicStateText()
                + "\n\n请从狼队收益、女巫用药收益和预言家信息收益三个角度综合决策，输出 JSON。";

        JsonNode json = callJson(system, user, 45);
        if (json == null) return null;
        String wolfTarget = resolveId(game, textOrNull(json, "wolfTarget"));
        Boolean witchSave = boolOrNull(json, "witchSave");
        String witchPoison = resolveId(game, textOrNull(json, "witchPoison"));
        String seerCheck = resolveId(game, textOrNull(json, "seerCheck"));
        log.info("Werewolf AI night plan: wolf={}, save={}, poison={}, check={}",
                wolfTarget, witchSave, witchPoison, seerCheck);
        return new WerewolfGame.NightPlan(wolfTarget, witchSave, witchPoison, seerCheck);
    }

    // ─────────────────────────────────────────────────────────────
    // Votes and hunter
    // ─────────────────────────────────────────────────────────────

    @Override
    public Map<String, String> chooseVotes(WerewolfGame game, List<WerewolfGame.Player> voters) {
        if (!configured || voters == null || voters.isEmpty()) return Map.of();
        String voterInfo = voters.stream()
                .map(p -> "- " + p.getName() + "(" + p.getId() + ")，身份:" + p.getRole().getLabel()
                        + "，怀疑对象:" + suspicionText(game))
                .collect(Collectors.joining("\n"));
        String alive = game.alivePlayersView().stream()
                .map(p -> "- " + p.getName() + "(" + p.getId() + ")"
                        + (p.isHuman() ? " [人类]" : "")
                        + (p.getClaimedRole() != null ? " [自称" + p.getClaimedRole() + "]" : ""))
                .collect(Collectors.joining("\n"));
        String checks = voters.stream()
                .filter(p -> p.getRole() == WerewolfGame.Role.SEER)
                .map(p -> p.getName() + "的查验记录: " + checksText(game, p))
                .collect(Collectors.joining("\n"));

        String system = """
                你是狼人杀桌游的 AI 决策核心，要为每个 AI 玩家独立决定白天放逐票。
                必须结合身份、查验、公开发言、投票历史、被怀疑程度和阵营收益判断。
                规则：
                1. 只能投存活的其他玩家，不能投自己。
                2. 狼人不要投狼队友；可以跟票、倒钩、甩锅，优先投掉威胁大的好人。
                3. 预言家优先投自己查到的存活狼人，并考虑是否已经公开跳身份。
                4. 村民、女巫、猎人应投最可疑的人，不要盲目跟票。
                5. 所有 AI 的票可以不同，模拟真实博弈。
                只输出 JSON 对象，键是 AI 玩家 ID，值是目标玩家 ID，例如：
                {"p2":"p5","p3":"p1"}
                不要 markdown，不要解释。
                """;
        String user = "当前第 " + game.getRound() + " 天，存活玩家：\n" + alive
                + "\n\n需要投票的 AI：\n" + voterInfo
                + (checks.isBlank() ? "" : "\n\n预言家私有信息：\n" + checks)
                + "\n\n近期公开事件：\n" + game.publicStateText()
                + "\n\n请为每个需要投票的 AI 给出目标 ID，输出 JSON。";

        JsonNode json = callJson(system, user, 30);
        if (json == null || !json.isObject()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        json.fields().forEachRemaining(entry -> {
            String voterId = resolveId(game, entry.getKey());
            String targetId = entry.getValue().isTextual() ? resolveId(game, entry.getValue().asText()) : null;
            if (voterId != null && targetId != null) {
                result.put(voterId, targetId);
            }
        });
        log.info("Werewolf AI votes: {}", result);
        return result;
    }

    @Override
    public String chooseHunterShot(WerewolfGame game, WerewolfGame.Player hunter) {
        if (!configured) return null;
        String system = """
                你是狼人杀中的猎人，刚刚出局，可以开枪带走一名存活玩家。
                请结合公开发言、投票历史、被怀疑程度和阵营收益，选择最可能是狼人的目标。
                只输出 JSON：{"targetId":"pX"}，不要解释，不要 markdown。
                """;
        String user = "第 " + game.getRound() + " 轮，猎人 " + hunter.getName()
                + " 准备开枪。\n存活玩家：\n"
                + game.alivePlayersView().stream()
                    .filter(p -> !p.getId().equals(hunter.getId()))
                    .map(p -> "- " + p.getName() + "(" + p.getId() + ")" + (p.isHuman() ? " [人类]" : ""))
                    .collect(Collectors.joining("\n"))
                + "\n\n近期公开事件：\n" + game.publicStateText()
                + "\n\n请输出 JSON。";
        JsonNode json = callJson(system, user, 30);
        if (json == null) return null;
        String target = resolveId(game, textOrNull(json, "targetId"));
        log.info("Werewolf AI hunter shot: {}", target);
        return target;
    }

    // ─────────────────────────────────────────────────────────────
    // Speeches
    // ─────────────────────────────────────────────────────────────

    public Map<String, String> generateSpeeches(WerewolfGame game) {
        if (!configured) return Map.of();
        List<WerewolfGame.Player> speakers = game.aliveAiPlayers();
        if (speakers.isEmpty()) return Map.of();
        String aiRoles = speakers.stream()
                .map(p -> p.getId() + " " + p.getName() + "（" + p.getRole().getLabel() + "）")
                .collect(Collectors.joining("\n"));
        String publicLog = game.publicStateText();

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

        String user = "当前是第 " + game.toView().round() + " 轮白天，存活玩家：\n"
                + game.allPlayers().stream()
                    .filter(WerewolfGame.Player::isAlive)
                    .map(p -> "- " + p.getName() + (p.isHuman() ? "（人类玩家）" : ""))
                    .collect(Collectors.joining("\n"))
                + "\n\n存活 AI 的秘密身份：\n" + aiRoles
                + "\n\n近期公开事件：\n" + publicLog
                + "\n\n请输出 JSON。";

        JsonNode json = callJson(system, user, 45);
        if (json == null) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        json.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) return;
            String speakerId = resolveId(game, entry.getKey());
            String value = entry.getValue().asText().trim();
            if (speakerId != null && !value.isEmpty()) result.put(speakerId, value);
        });
        if (!result.isEmpty()) log.info("Werewolf AI generated {} speeches", result.size());
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // Model helpers
    // ─────────────────────────────────────────────────────────────

    private JsonNode callJson(String system, String user, int timeoutSeconds) {
        try {
            String raw = CompletableFuture.supplyAsync(() -> chatClient.prompt()
                            .system(system)
                            .user(user)
                            .call()
                            .content(), executor)
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            return parseJson(raw);
        } catch (Exception ex) {
            log.warn("Werewolf AI model call failed; falling back: {}", ex.getMessage());
            return null;
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

    private Boolean boolOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        return value.asBoolean();
    }

    private boolean hasAntidote(WerewolfGame.Player player) {
        try {
            return player.hasAntidote();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasPoison(WerewolfGame.Player player) {
        try {
            return player.hasPoison();
        } catch (Exception ex) {
            return false;
        }
    }

    private String checksText(WerewolfGame game, WerewolfGame.Player seer) {
        if (seer.getRole() != WerewolfGame.Role.SEER) return "无";
        Map<String, WerewolfGame.Role> checks = game.seerChecksView();
        if (checks.isEmpty()) return "还没有查验记录";
        return checks.entrySet().stream()
                .map(e -> {
                    WerewolfGame.Player p = game.allPlayers().stream()
                            .filter(x -> x.getId().equals(e.getKey())).findFirst().orElse(null);
                    return (p == null ? e.getKey() : p.getName()) + "=" + e.getValue().getLabel();
                })
                .collect(Collectors.joining("，"));
    }

    private String suspicionText(WerewolfGame game) {
        Map<String, Integer> suspicion = game.suspicionView();
        if (suspicion.isEmpty()) return "暂无";
        return suspicion.entrySet().stream()
                .map(e -> {
                    WerewolfGame.Player p = game.allPlayers().stream()
                            .filter(x -> x.getId().equals(e.getKey())).findFirst().orElse(null);
                    return (p == null ? e.getKey() : p.getName()) + ":" + e.getValue();
                })
                .collect(Collectors.joining("，"));
    }

    private String nameOf(WerewolfGame game, String id) {
        return game.allPlayers().stream()
                .filter(p -> p.getId().equals(id))
                .map(WerewolfGame.Player::getName)
                .findFirst().orElse(id);
    }

    /**
     * Models sometimes answer with a seat/name ("3号 阿澈") instead of p3.
     * Resolve both forms so a valid model decision does not silently fall back.
     */
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
