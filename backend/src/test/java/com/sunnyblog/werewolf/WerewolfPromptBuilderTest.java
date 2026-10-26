package com.sunnyblog.werewolf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WerewolfPromptBuilderTest {

    private WerewolfGame.Player human(WerewolfGame game) {
        return game.allPlayers().stream().filter(WerewolfGame.Player::isHuman).findFirst().orElseThrow();
    }

    @Test
    void gameIdIsFirstAndSectionsAreOrdered() {
        WerewolfGame game = new WerewolfGame("月白", 6, WerewolfGame.Role.SEER);
        WerewolfGame.Player seer = human(game);
        WerewolfPromptBuilder.Prompt prompt =
                WerewolfPromptBuilder.build(game, seer, "请选择今晚的查验目标。");

        assertTrue(prompt.system().startsWith("[GAME_ID] " + game.getId()));
        assertTrue(prompt.system().indexOf("【公共广播") < prompt.system().indexOf("【角色提示】"));
        assertTrue(prompt.system().indexOf("【角色提示】") < prompt.system().indexOf("【当前任务】"));
        assertTrue(prompt.system().contains("你的身份是预言家"));
        assertTrue(prompt.system().contains("查验"));
    }

    @Test
    void taskChangesOnlyTheTailSoTheSharedPrefixStaysCacheable() {
        WerewolfGame game = new WerewolfGame("月白", 6, WerewolfGame.Role.SEER);
        WerewolfGame.Player seer = human(game);
        String first = WerewolfPromptBuilder.build(game, seer, "任务 A").system();
        String second = WerewolfPromptBuilder.build(game, seer, "任务 B").system();
        String firstPrefix = first.substring(0, first.indexOf("【当前任务】"));
        String secondPrefix = second.substring(0, second.indexOf("【当前任务】"));
        assertEquals(firstPrefix, secondPrefix);
        assertNotEquals(first, second);
    }

    @Test
    void differentGamesDoNotShareThePrefix() {
        WerewolfGame first = new WerewolfGame("月白", 6, WerewolfGame.Role.SEER);
        WerewolfGame second = new WerewolfGame("月白", 6, WerewolfGame.Role.SEER);
        String a = WerewolfPromptBuilder.build(first, human(first), "任务").system();
        String b = WerewolfPromptBuilder.build(second, human(second), "任务").system();
        assertFalse(a.startsWith("[GAME_ID] " + second.getId()));
        assertNotEquals(a.substring(0, Math.min(a.length(), 80)),
                b.substring(0, Math.min(b.length(), 80)));
    }

    @Test
    void roleProfilesExistForEveryRole() {
        for (WerewolfGame.Role role : WerewolfGame.Role.values()) {
            WerewolfGame game = new WerewolfGame("月白", 6, role);
            String prompt = WerewolfPromptBuilder.build(game, human(game), "任务").system();
            assertTrue(prompt.contains("【角色提示】"));
            assertTrue(prompt.contains("你的身份是"));
        }
    }
}
