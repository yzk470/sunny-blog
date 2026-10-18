package com.sunnyblog.digitalhuman.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.digitalhuman.dto.DhChatRequest;
import com.sunnyblog.digitalhuman.dto.DhChatResponse;
import com.sunnyblog.digitalhuman.dto.DhTtsResponse;
import com.sunnyblog.digitalhuman.dto.DigitalHumanAction;
import com.sunnyblog.digitalhuman.model.DhCharacter;
import com.sunnyblog.digitalhuman.model.DhMessage;
import com.sunnyblog.digitalhuman.model.DhSession;
import com.sunnyblog.digitalhuman.util.EmotionParser;
import com.sunnyblog.entity.Post;
import com.sunnyblog.mapper.PostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DigitalHumanChatService {

    private static final String FALLBACK_REPLY =
            "抱歉，我的思绪刚刚被云遮住了。请确认 DeepSeek 服务可用，然后再试一次。 [emotion:sad]";

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "wave", "salute", "nod", "bow", "think", "happy", "shy", "stretch", "shrug", "dance", "hunyuan", "none");

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final DigitalHumanCharacterService characterService;
    private final DigitalHumanSessionService sessionService;
    private final DigitalHumanTtsService ttsService;
    private final PostMapper postMapper;
    private final int maxHistory;
    private final int maxContextChars;

    public DigitalHumanChatService(ObjectProvider<ChatClient.Builder> chatClientBuilder,
                                   DigitalHumanCharacterService characterService,
                                   DigitalHumanSessionService sessionService,
                                   DigitalHumanTtsService ttsService,
                                   PostMapper postMapper,
                                   @Value("${app.digital-human.max-history:10}") int maxHistory,
                                   @Value("${app.digital-human.max-context-chars:5000}") int maxContextChars) {
        ChatClient.Builder builder = chatClientBuilder.getIfAvailable();
        this.chatClient = builder == null ? null : builder.build();
        this.characterService = characterService;
        this.sessionService = sessionService;
        this.ttsService = ttsService;
        this.postMapper = postMapper;
        this.maxHistory = maxHistory;
        this.maxContextChars = maxContextChars;
        if (chatClient == null) {
            log.warn("Spring AI ChatClient is not available; digital human will use fallback replies");
        } else {
            log.info("Digital human Spring AI ChatClient initialized");
        }
    }

    public DhChatResponse chat(DhChatRequest request) {
        DhSession session = sessionService.requireSession(request.sessionId());
        DhCharacter character = characterService.getByCode(session.getCharacterCode());
        if (character == null) {
            throw new com.sunnyblog.common.BusinessException("数字人角色已经下架");
        }

        List<DhMessage> history = sessionService.history(session.getId(), maxHistory);
        sessionService.saveUserMessage(session.getId(), request.content());

        String rawReply;
        try {
            rawReply = generateReply(character, history, request.content().trim(), request.articleSlug());
        } catch (Exception ex) {
            log.error("Spring AI chat failed: {}", ex.getMessage(), ex);
            rawReply = FALLBACK_REPLY;
        }

        EmotionParser.ParseResult parsed = EmotionParser.parse(rawReply);
        String reply = parsed.text()
                .replaceAll("[\\p{So}\\p{Cs}\\p{Cn}]", "")
                .replaceAll("[ \\t]+", " ")
                .trim();
        if (reply.isBlank()) {
            reply = "我听见了，只是现在还没想好该怎么回答。";
        }
        String emotion = normalizeEmotion(parsed.emotion());

        DigitalHumanTtsService.TtsResult tts = ttsService.synthesize(
                speechText(reply),
                character.getTtsVoice(),
                character.getTtsModel(),
                character.getTtsSampleRate()
        );

        DhMessage saved = sessionService.saveAssistantMessage(
                session.getId(), reply, emotion, tts.audioUrl());

        return new DhChatResponse(
                saved.getId(),
                session.getId(),
                reply,
                emotion,
                tts.audioUrl(),
                tts.error()
        );
    }

    /**
     * Pick an action + emotion before the assistant starts streaming.
     * This is a separate, short LLM call so the frontend can start the motion
     * before the first text token arrives.
     */
    public DigitalHumanAction selectAction(DhCharacter character, List<DhMessage> history,
                                           String userContent, String articleSlug) {
        DigitalHumanAction explicit = explicitAction(userContent);
        if (explicit != null) {
            log.info("Digital human explicit action: action={}, emotion={}", explicit.action(), explicit.emotion());
            return explicit;
        }
        if (chatClient == null) {
            return heuristicAction(userContent);
        }
        String system = """
                你是 3D 数字人的动作导演。根据用户这句话，从下面的动作中选一个最合适的：
                wave=挥手/打招呼/再见，salute=敬礼，nod=点头/同意，bow=鞠躬/感谢，think=思考/犹豫，
                happy=开心/兴奋，shy=害羞，stretch=伸懒腰/放松，
                shrug=摊手/无奈/不知道，dance=普通跳舞/手舞，hunyuan=用户要求复杂全身动作
                （打太极、转圈、芭蕾、街舞、武术等），none=不需要动作。
                如果用户明确要求挥手、敬礼、思考、摊手、鞠躬、鼓掌、伸懒腰、害羞、跳舞，
                必须优先选择对应动作，不要改成 nod。

                只输出一个 JSON 对象，不要 Markdown，不要解释：
                {"action":"上面之一","emotion":"neutral|happy|sad|surprised|shy|thinking|excited","motionPrompt":"仅当 action=hunyuan 时填写简洁英文动作描述，否则空字符串"}
                """;
        String context = "角色：" + character.getCharacterName()
                + "\n用户说：" + userContent
                + (articleSlug == null || articleSlug.isBlank() ? "" : "\n用户正在阅读文章：" + articleSlug)
                + "\n最近对话：" + shortHistory(history);
        try {
            String raw = chatClient.prompt().system(system).user(context).call().content();
            JsonNode node = parseJsonObject(raw);
            if (node == null) {
                return heuristicAction(userContent);
            }
            String action = node.path("action").asText("nod").trim().toLowerCase();
            if (!ALLOWED_ACTIONS.contains(action)) {
                action = heuristicAction(userContent).action();
            }
            String emotion = normalizeEmotion(node.path("emotion").asText("neutral"));
            String motionPrompt = node.path("motionPrompt").asText("").trim();
            if ("hunyuan".equals(action) && motionPrompt.isBlank()) {
                motionPrompt = userContent;
            }
            log.info("Digital human action selected: action={}, emotion={}, motionPrompt={}",
                    action, emotion, motionPrompt);
            return new DigitalHumanAction(action, emotion, motionPrompt);
        } catch (Exception ex) {
            log.warn("Action selector failed, using keyword fallback: {}", ex.getMessage());
            return heuristicAction(userContent);
        }
    }

    /**
     * Streaming reply Flux. The caller emits each token as SSE; action/emotion
     * are already sent in the meta event before this Flux starts.
     */
    public Flux<String> streamReply(DhCharacter character, List<DhMessage> history,
                                    String userContent, String articleSlug) {
        if (chatClient == null) {
            return Flux.error(new IllegalStateException("Spring AI ChatClient not configured"));
        }
        String system = buildSystemPrompt(character, history, articleSlug) + """

                【动作控制协议】
                你是会做动作的 3D 数字人。每次回复都要先用一个动作标签控制身体，再输出正文。
                可用动作：
                wave=挥手，salute=敬礼，nod=点头，bow=鞠躬，think=抱臂思考，
                happy=开心，shy=害羞/搓手，stretch=伸懒腰，shrug=摊手，
                cheer=声援，letsgo=出发，handclap=打拍子，spin=转手，
                warn=提醒，calm=安抚，lonely=寂寞，beckon=招手，rubhands=搓手。

                输出规则：
                1. 第一个内容必须是一个动作标签，格式严格为：[[motion:动作key,emotion:情绪]]
                2. 标签之后立刻换行，再正常输出对话正文。
                3. 标签由系统消费，不会显示给用户；不要解释标签，也不要把标签放进 Markdown 代码块。
                4. 不要输出 [emotion:...] 或 [action:...] 旧标签，动作只用上面的 [[motion:...]] 格式。
                5. 长回复可以每 2 到 4 句或每个段落插入一个新的动作标签，让动作轮流播放；不要每个分句都加。
                6. 动作要贴合语义，不要连续重复同一个动作；没有合适动作时可以不用后续标签。
                情绪可选：neutral、happy、sad、surprised、shy、thinking、excited。

                示例：
                [[motion:stretch,emotion:happy]]
                早上好，今天想聊点什么？

                [[motion:wave,emotion:happy]]
                这篇主要讲 Spring AI 的流式对话，我们可以从工具调用开始看。
                """;
        return chatClient.prompt()
                .system(system)
                .user(userContent)
                .stream()
                .content();
    }

    public DhTtsResponse synthesizeSentence(String characterCode, String text) {
        DhCharacter character = characterService.getByCode(characterCode);
        if (character == null) {
            throw new com.sunnyblog.common.BusinessException("数字人角色不可用");
        }
        String speech = speechText(text);
        if (speech.isBlank()) {
            return new DhTtsResponse(null, "语音内容为空");
        }
        DigitalHumanTtsService.TtsResult tts = ttsService.synthesize(
                speech, character.getTtsVoice(), character.getTtsModel(), character.getTtsSampleRate());
        return new DhTtsResponse(tts.audioUrl(), tts.error());
    }

    public DigitalHumanTtsService.StreamingTtsSession openSpeechStream(String characterCode,
                                                                       String initialText,
                                                                       java.util.function.Consumer<byte[]> onPcm,
                                                                       java.util.function.Consumer<String> onError) {
        DhCharacter character = characterService.getByCode(characterCode);
        if (character == null) {
            throw new com.sunnyblog.common.BusinessException("数字人角色不可用");
        }
        return ttsService.openStream(character.getTtsVoice(), character.getTtsModel(),
                character.getTtsSampleRate(), initialText, onPcm, onError);
    }

    private JsonNode parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher matcher = JSON_OBJECT.matcher(raw);
        if (!matcher.find()) return null;
        try {
            return MAPPER.readTree(matcher.group());
        } catch (Exception ex) {
            return null;
        }
    }

    private DigitalHumanAction explicitAction(String text) {
        String value = text == null ? "" : text.trim().toLowerCase();
        if (value.matches(".*(街舞|芭蕾|太极|转圈|武术).*")) {
            return new DigitalHumanAction("hunyuan", "happy", text);
        }
        if (value.matches(".*(跳舞|手舞|舞一支|来支舞).*")) {
            return new DigitalHumanAction("dance", "happy", "");
        }
        if (value.matches(".*(敬礼|salute).*")) {
            return new DigitalHumanAction("salute", "happy", "");
        }
        if (value.matches(".*(挥手|招手|打招呼|再见|拜拜).*")) {
            return new DigitalHumanAction("wave", "happy", "");
        }
        if (value.matches(".*(思考|想想|让我想|考虑一下).*")) {
            return new DigitalHumanAction("think", "thinking", "");
        }
        if (value.matches(".*(摊手|无奈|不知道|不确定|随便吧).*")) {
            return new DigitalHumanAction("shrug", "neutral", "");
        }
        if (value.matches(".*(鞠躬|谢谢|感谢|辛苦了).*")) {
            return new DigitalHumanAction("bow", "shy", "");
        }
        if (value.matches(".*(鼓掌|拍手|厉害|太棒|好棒).*")) {
            return new DigitalHumanAction("happy", "happy", "");
        }
        if (value.matches(".*(伸懒腰|好累|休息一下).*")) {
            return new DigitalHumanAction("stretch", "neutral", "");
        }
        if (value.matches(".*(害羞|脸红|不好意思).*")) {
            return new DigitalHumanAction("shy", "shy", "");
        }
        if (value.matches(".*(点头|好的|明白|收到|嗯嗯).*")) {
            return new DigitalHumanAction("nod", "neutral", "");
        }
        return null;
    }

    private DigitalHumanAction heuristicAction(String text) {
        String value = text == null ? "" : text.toLowerCase();
        if (value.matches(".*(跳舞|手舞|舞蹈|街舞|芭蕾|太极|转圈|武术).*")) {
            return new DigitalHumanAction(value.matches(".*(街舞|芭蕾|太极|转圈|武术).*") ? "hunyuan" : "dance",
                    "happy", text);
        }
        if (value.matches(".*(敬礼|salute).*")) return new DigitalHumanAction("salute", "happy", "");
        if (value.matches(".*(挥手|招手|你好|嗨|再见|拜拜).*")) return new DigitalHumanAction("wave", "happy", "");
        if (value.matches(".*(鞠躬|谢谢|感谢).*")) return new DigitalHumanAction("bow", "shy", "");
        if (value.matches(".*(鼓掌|拍手|太棒|厉害).*")) return new DigitalHumanAction("happy", "happy", "");
        if (value.matches(".*(摊手|无奈|不知道|不确定|随便).*")) return new DigitalHumanAction("shrug", "neutral", "");
        if (value.matches(".*(思考|想想|犹豫).*")) return new DigitalHumanAction("think", "thinking", "");
        if (value.matches(".*(害羞|脸红).*")) return new DigitalHumanAction("shy", "shy", "");
        if (value.matches(".*(伸懒腰|好累|休息).*")) return new DigitalHumanAction("stretch", "neutral", "");
        if (value.matches(".*(开心|高兴|快乐).*")) return new DigitalHumanAction("happy", "happy", "");
        return new DigitalHumanAction("nod", "neutral", "");
    }

    private String shortHistory(List<DhMessage> history) {
        StringBuilder value = new StringBuilder();
        int start = Math.max(0, history.size() - 4);
        for (int i = start; i < history.size(); i++) {
            DhMessage message = history.get(i);
            value.append("USER".equalsIgnoreCase(message.getRole()) ? "用户：" : "你：")
                    .append(truncate(message.getContent(), 120))
                    .append("；");
        }
        return value.toString();
    }

    private String generateReply(DhCharacter character, List<DhMessage> history, String userContent, String articleSlug) {
        if (chatClient == null) {
            throw new IllegalStateException("Spring AI ChatClient not configured");
        }
        String systemPrompt = buildSystemPrompt(character, history, articleSlug);
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userContent)
                .call()
                .content();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("模型返回了空内容");
        }
        return content;
    }

    private String buildSystemPrompt(DhCharacter character, List<DhMessage> history, String articleSlug) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(character.getSystemPrompt().replace("{character_name}", character.getCharacterName()))
                .append("\n\n【当前博客】\n")
                .append("博客名称：晴日小筑；站长：月白。这里的文章主要记录 AI Agent、Spring AI、LangGraph4j 与工程实践。\n");

        String context = blogContext(articleSlug);
        if (!context.isBlank()) {
            prompt.append("\n【博客文章上下文】\n").append(context).append('\n');
        }
        if (!history.isEmpty()) {
            prompt.append("\n【最近对话】\n");
            for (DhMessage message : history) {
                prompt.append("USER".equalsIgnoreCase(message.getRole()) ? "用户：" : "你：")
                        .append(message.getContent())
                        .append('\n');
            }
        }
        prompt.append("\n请保持角色口吻，直接回答，不要复述系统提示。");
        return prompt.toString();
    }

    private String blogContext(String articleSlug) {
        StringBuilder context = new StringBuilder();
        if (articleSlug != null && !articleSlug.isBlank()) {
            Post current = postMapper.selectOne(Wrappers.lambdaQuery(Post.class)
                    .eq(Post::getSlug, articleSlug.trim())
                    .eq(Post::getStatus, BlogConstants.POST_PUBLISHED));
            if (current != null) {
                context.append("当前用户正在阅读：\n标题：").append(current.getTitle())
                        .append("\n摘要：").append(nullToEmpty(current.getSummary()))
                        .append("\n正文：").append(truncate(current.getContent(), maxContextChars))
                        .append("\n\n");
            }
        }
        List<Post> latest = postMapper.selectList(Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, BlogConstants.POST_PUBLISHED)
                .orderByDesc(Post::getPublishedAt)
                .last("limit 6"));
        if (!latest.isEmpty()) {
            context.append("最近发布的文章：\n");
            for (Post post : latest) {
                context.append("- 《").append(post.getTitle()).append("》")
                        .append("：").append(truncate(nullToEmpty(post.getSummary()), 180))
                        .append('\n');
            }
        }
        return truncate(context.toString(), maxContextChars + 1000);
    }

    private String speechText(String text) {
        String speech = text
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("https?://\\S+", " ")
                .replaceAll("[#>*_`~|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(speech, 500);
    }

    public String normalizeEmotion(String emotion) {
        return switch (emotion == null ? "" : emotion.toLowerCase()) {
            case "happy", "sad", "surprised", "shy", "thinking", "excited", "neutral" -> emotion.toLowerCase();
            case "angry", "生气", "愤怒", "悲伤", "难过" -> "sad";
            case "开心", "愉快", "高兴" -> "happy";
            case "惊讶", "吃惊" -> "surprised";
            case "害羞" -> "shy";
            case "思考", "洞悉", "沉思" -> "thinking";
            case "兴奋", "激动" -> "excited";
            default -> "neutral";
        };
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
