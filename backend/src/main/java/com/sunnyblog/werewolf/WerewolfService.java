package com.sunnyblog.werewolf;

import com.sunnyblog.common.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
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
                    attachAi(game);
                    String humanId = game.humanPlayer().getId();
                    game.setEventListener(new WerewolfGame.GameEventListener() {
                        @Override
                        public void onLog(WerewolfGame.LogEntry entry) {
                            if (entry.privateTo() != null && !entry.privateTo().equals(humanId)) return;
                            if ("speech".equals(entry.type())) return;
                            sendQuietly(emitter, "log", new WerewolfGame.LogView(
                                    entry.seq(), entry.round(), entry.phase(), entry.phaseLabel(),
                                    entry.type(), entry.text(), entry.privateTo() != null
                            ));
                        }

                        @Override
                        public void onSnapshot(WerewolfGame.GameView view) {
                            sendQuietly(emitter, "snapshot", view);
                        }

                        @Override
                        public void onThinking(String text) {
                            sendQuietly(emitter, "thinking", Map.of("text", text));
                        }
                    });
                    send(emitter, "thinking", Map.of("text", "AI 正在整理这一轮的局势…"));
                    String action = request.action() == null ? "ADVANCE" : request.action();
                    if ("AUTO".equals(action)) {
                        streamAuto(game, emitter);
                    } else {
                        switch (action) {
                            case "ACTION" -> game.humanAction(request.type(), request.targetId(), request.text());
                            case "SPEAK" -> game.humanAction("SPEAK", null, request.text());
                            default -> game.advance();
                        }
                        if (game.getPhase() == WerewolfGame.Phase.DISCUSSION && game.hasPendingDiscussion()) {
                            // Publish the public dawn/discussion events and any human
                            // speech before streaming the next AI line.
                            send(emitter, "state", game.toView());
                            streamDiscussion(game, emitter, false);
                        }
                    }
                    send(emitter, "state", game.toView());
                    send(emitter, "done", Map.of("ok", true));
                } catch (IllegalStateException | IllegalArgumentException ex) {
                    sendQuietly(emitter, "error", Map.of("message", ex.getMessage()));
                } catch (Exception ex) {
                    log.error("Werewolf stream failed", ex);
                    sendQuietly(emitter, "error", Map.of("message", "本回合处理失败，请继续下一步"));
                } finally {
                    game.setEventListener(null);
                    emitter.complete();
                }
            }
        });
        return emitter;
    }

    /**
     * Walks the discussion one speaker at a time. Real model tokens are pushed
     * as they arrive. When the human's turn comes and autoPlay is false, the
     * stream pauses and the client sends the human speech through /stream SPEAK.
     */
    private boolean streamDiscussion(WerewolfGame game, SseEmitter emitter, boolean autoHuman) {
        while (game.hasPendingDiscussion()) {
            WerewolfGame.Player speaker = game.nextDiscussionSpeaker();
            if (speaker == null) break;
            if (speaker.isHuman() && !autoHuman) {
                sendQuietly(emitter, "speaking_turn", Map.of(
                        "speakerId", speaker.getId(),
                        "speakerName", speaker.getName(),
                        "seat", speaker.getSeat()
                ));
                sendQuietly(emitter, "state", game.toView());
                return false;
            }
            sendQuietly(emitter, "thinking", Map.of("text", speaker.getName() + " 正在组织语言…"));
            sendQuietly(emitter, "speech_start", Map.of(
                    "speakerId", speaker.getId(),
                    "speakerName", speaker.getName(),
                    "seat", speaker.getSeat()
            ));
            String fallback = game.fallbackSpeech(speaker);
            String text = aiService.streamSpeech(game, speaker, fallback,
                    token -> sendQuietly(emitter, "token", Map.of("text", token)));
            sendQuietly(emitter, "speech_end", Map.of("text", text));
            game.recordSpeech(speaker.getId(), text);
        }
        game.finishDiscussion();
        game.advance();
        return true;
    }

    private void streamAuto(WerewolfGame game, SseEmitter emitter) {
        int guard = 0;
        while (!game.isOver() && guard++ < 120) {
            if (game.getPhase() == WerewolfGame.Phase.DISCUSSION) {
                if (!streamDiscussion(game, emitter, true)) return;
                continue;
            }
            if (game.hasWaiting()) {
                game.autoResolveWaiting();
                continue;
            }
            game.advance();
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
            attachAi(game);
            game.advance();
            return game.toView();
        }
    }

    public WerewolfGame.GameView action(String id, String type, String targetId, String text) {
        WerewolfGame game = game(id);
        synchronized (game) {
            attachAi(game);
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
            attachAi(game);
            game.autoPlay();
            return game.toView();
        }
    }

    private void attachAi(WerewolfGame game) {
        game.setDecisionProvider(aiService.isConfigured() ? aiService : null);
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
