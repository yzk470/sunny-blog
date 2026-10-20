package com.sunnyblog.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSkillsZipTest {

    @TempDir
    Path temp;

    @Test
    void importsDirectorySkillWithScriptsAndResources() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.setDir(temp.toString());
        AgentSkillsService service = new AgentSkillsService(properties);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            add(zip, "pptx/SKILL.md", "---\nname: pptx\ndescription: slides\n---\nUse editing.md");
            add(zip, "pptx/editing.md", "edit workflow");
            add(zip, "pptx/scripts/build.py", "print('ok')");
        }
        AgentSkillsService.Skill skill = service.importZip(out.toByteArray(), "pptx");
        assertEquals("pptx", skill.slug());
        assertTrue(skill.resources().contains("editing.md"));
        assertTrue(skill.resources().contains("scripts/build.py"));
    }

    private void add(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
