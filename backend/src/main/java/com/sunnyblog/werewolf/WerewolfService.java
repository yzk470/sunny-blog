package com.sunnyblog.werewolf;

import com.sunnyblog.common.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class WerewolfService {

    private static final int MAX_GAMES = 120;
    private final Map<String, Session> games = new ConcurrentHashMap<>();
    private final WerewolfAiService aiService;
    private final ExecutorService streamExecutor = Executors.newFixedThreadPool(3);

    private record Session(WerewolfGame game, Instant createdAt) {}

    public record StreamRequest(String action, String type, String targetId, String text) {}

    public record AiStatus(boolean configured) {}

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdownNow();
    }

    public WerewolfGame.GameView create(String playerName, Integer playerCount, String roleCode) {
        if (games.size() >= MAX_GAMES) {
            games.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().createdAt()))
                    .map(Map.Entry::getKey)
                    .ifPresent(games::remove);
        }
        WerewolfGame.Role preferred = parseRole(roleCode);
        WerewolfGame game = new WerewolfGame(playerName, playerCount == null ? 8 : playerCount, preferred);
        games.put(game.getId(), new Session(game, Instant.now()));
        return game.toView();
    }

    public AiStatus aiStatus() {
        return new AiStatus(aiService.isConfigured());
    }

    public SseEmitter stream(String id, StreamRequest request) {
        WerewolfGame game = game(id);
        SseEmitter emitter = new SseEmitter(0L);
        streamExecutor.execute(() -> {
            synchronized (game) {
                try {
                    send(emitter, "thinking", Map.of("text", "AI 正在整理这一轮的局势…"));
                    game.setSpeechStreamer(new ModelSpeechStreamer(emitter, game));
                    String action = request.action() == null ? "ADVANCE" : request.action();
                    switch (action) {
                        case "ACTION" -> game.humanAction(request.type(), request.targetId(), request.text());
                        case "SPEAK" -> game.humanAction("SPEAK", null, request.text());
                        case "AUTO" -> game.autoPlay();
                        default -> game.advance();
                    }
                    send(emitter, "state", game.toView());
                    send(emitter, "done", Map.of("ok", true));
                } catch (IllegalStateException | IllegalArgumentException ex) {
                    sendQuietly(emitter, "error", Map.of("message", ex.getMessage()));
                } catch (Exception ex) {
                    sendQuietly(emitter, "error", Map.of("message", "本回合处理失败，请继续下一步"));
                } finally {
                    game.setSpeechStreamer(null);
                    emitter.complete();
                }
            }
        });
        return emitter;
    }

    private final class ModelSpeechStreamer implements WerewolfGame.SpeechStreamer {
        private final SseEmitter emitter;
        private final WerewolfGame game;
        private Map<String, String> plan;
        private boolean loaded;

        private ModelSpeechStreamer(SseEmitter emitter, WerewolfGame game) {
            this.emitter = emitter;
            this.game = game;
        }

        @Override
        public String speak(WerewolfGame.Player speaker, String fallback) {
            if (!loaded) {
                loaded = true;
                plan = aiService.generateSpeeches(game);
                if (plan.isEmpty()) {
                    sendQuietly(emitter, "thinking", Map.of("text", "本轮由本地 Agent 接替发言…"));
                }
            }
            String text = plan.getOrDefault(speaker.getId(), fallback);
            sendQuietly(emitter, "speech_start", Map.of(
                    "speakerId", speaker.getId(),
                    "speakerName", speaker.getName(),
                    "seat", speaker.getSeat()
            ));
            for (int i = 0; i < text.length(); i += 2) {
                int end = Math.min(text.length(), i + 2);
                sendQuietly(emitter, "token", Map.of("text", text.substring(i, end)));
                try {
                    Thread.sleep(18);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            sendQuietly(emitter, "speech_end", Map.of("text", text));
            return text;
        }
    }

    private void send(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private void sendQuietly(SseEmitter emitter, String name, Object data) {
        try {
            send(emitter, name, data);
        } catch (IOException ignored) {
            // The browser may have navigated away mid-stream.
        }
    }

    public WerewolfGame.GameView get(String id) {
        return game(id).toView();
    }

    public WerewolfGame.GameView advance(String id) {
        WerewolfGame game = game(id);
        synchronized (game) {
            game.advance();
            return game.toView();
        }
    }

    public WerewolfGame.GameView action(String id, String type, String targetId, String text) {
        WerewolfGame game = game(id);
        synchronized (game) {
            try {
                game.humanAction(type, targetId, text);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                throw new BusinessException(ex.getMessage());
            }
            return game.toView();
        }
    }

    public WerewolfGame.GameView auto(String id) {
        WerewolfGame game = game(id);
        synchronized (game) {
            game.autoPlay();
            return game.toView();
        }
    }

    public void delete(String id) {
        games.remove(id);
    }

    private WerewolfGame game(String id) {
        Session session = games.get(id);
        if (session == null) throw new BusinessException(404, "这局游戏已经结束或不存在");
        return session.game();
    }

    private WerewolfGame.Role parseRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank() || "random".equalsIgnoreCase(roleCode)) return null;
        try {
            return WerewolfGame.Role.valueOf(roleCode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("未知的身份：" + roleCode);
        }
    }
}
