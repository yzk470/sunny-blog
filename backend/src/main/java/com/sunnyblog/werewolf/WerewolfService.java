package com.sunnyblog.werewolf;

import com.sunnyblog.common.BusinessException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WerewolfService {

    private static final int MAX_GAMES = 120;
    private final Map<String, Session> games = new ConcurrentHashMap<>();

    private record Session(WerewolfGame game, Instant createdAt) {}

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
