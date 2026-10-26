package com.sunnyblog.digitalhuman;

import com.sunnyblog.digitalhuman.config.DigitalHumanPersonas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitalHumanPersonasTest {

    @Test
    void yaoguangUsesYuequeIdentityAndTimeline() {
        String prompt = DigitalHumanPersonas.YAOGUANG;
        assertTrue(prompt.contains("玉阙"));
        assertTrue(prompt.contains("戎韬将军"));
        assertTrue(prompt.contains("十方光映法界"));
        assertTrue(prompt.contains("建木之乱"));
        assertFalse(prompt.contains("来自仙舟罗浮的策士"));
    }

    @Test
    void jingliuKeepsCloudQuintetTimeline() {
        String prompt = DigitalHumanPersonas.JINGLIU;
        assertTrue(prompt.contains("无罅飞光"));
        assertTrue(prompt.contains("云上五骁"));
        assertTrue(prompt.contains("饮月之乱"));
        assertTrue(prompt.contains("魔阴身"));
        assertTrue(prompt.contains("persona:v2"));
    }

    @Test
    void legacyDetectionOnlyMatchesOldSeededPrompts() {
        assertTrue(DigitalHumanPersonas.isLegacy(null));
        assertTrue(DigitalHumanPersonas.isLegacy("你是爻光，来自仙舟罗浮的策士"));
        assertTrue(DigitalHumanPersonas.isLegacy("你是镜流，来自星穹铁道的剑客"));
        assertFalse(DigitalHumanPersonas.isLegacy(DigitalHumanPersonas.YAOGUANG));
        assertFalse(DigitalHumanPersonas.isLegacy("后台自定义人设，不要覆盖"));
    }
}
