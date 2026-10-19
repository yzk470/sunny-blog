package com.sunnyblog.werewolf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WerewolfGameTest {

    @Test
    void autoGamesAlwaysFinish() {
        WerewolfGame.Role[] roles = {
                null,
                WerewolfGame.Role.WEREWOLF,
                WerewolfGame.Role.SEER,
                WerewolfGame.Role.WITCH,
                WerewolfGame.Role.HUNTER,
                WerewolfGame.Role.VILLAGER,
        };
        for (int count : new int[]{6, 8, 10, 12}) {
            for (WerewolfGame.Role role : roles) {
                for (int i = 0; i < 12; i++) {
                    WerewolfGame game = new WerewolfGame("测试玩家", count, role);
                    game.autoPlay();
                    WerewolfGame.GameView view = game.toView();
                    assertTrue(view.over(), "auto game should finish");
                    assertNotNull(view.winner(), "winner should be set");
                    assertEquals(count, view.players().size());
                    assertTrue(
                            "WEREWOLF".equals(view.winner()) || "VILLAGER".equals(view.winner()),
                            "winner should be a valid camp"
                    );
                    assertTrue(view.logs().stream().anyMatch(l -> "result".equals(l.type())),
                            "game should log a final result");
                }
            }
        }
    }

    @Test
    void humanCanResolveEveryWaitingDecision() {
        for (WerewolfGame.Role role : new WerewolfGame.Role[]{
                WerewolfGame.Role.WEREWOLF,
                WerewolfGame.Role.SEER,
                WerewolfGame.Role.WITCH,
                WerewolfGame.Role.VILLAGER,
                WerewolfGame.Role.HUNTER,
        }) {
            for (int attempt = 0; attempt < 20; attempt++) {
                WerewolfGame game = new WerewolfGame("月白", 12, role);
                int guard = 0;
                while (!game.toView().over() && guard++ < 220) {
                    WerewolfGame.GameView view = game.toView();
                    if ("DISCUSSION".equals(view.phase())) {
                        game.autoDiscussRemaining();
                        game.advance();
                        continue;
                    }
                    if (view.waiting() == null) {
                        game.advance();
                        continue;
                    }
                    if (view.waiting().options().isEmpty()) {
                        break;
                    }
                    WerewolfGame.Option option = view.waiting().options().get(0);
                    game.humanAction(view.waiting().type(), option.id(), null);
                }
                WerewolfGame.GameView end = game.toView();
                assertTrue(end.over(), "human game should finish for role " + role);
                assertFalse(end.logs().isEmpty());
            }
        }
    }
}
