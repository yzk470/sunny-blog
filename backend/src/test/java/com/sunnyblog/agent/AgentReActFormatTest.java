package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReActFormatTest {

    @Test
    void parsesDsmlToolMarkupAndRetries() {
        ObjectMapper mapper = new ObjectMapper();
        AgentLlmService llm = mock(AgentLlmService.class);
        when(llm.parseJson(anyString())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            if (raw.contains("DSML")) return null;
            return mapper.readTree(raw);
        });
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        <｜｜DSML｜｜ calls>
                        <｜｜DSML｜｜ invoke name="read_file">
                        <｜｜DSML｜｜ parameter name="scope" string="true">workspace</｜｜DSML｜｜ parameter>
                        <｜｜DSML｜｜ parameter name="path" string="true">scripts/a.txt</｜｜DSML｜｜ parameter>
                        </｜｜DSML｜｜ invoke>
                        </｜｜DSML｜｜ calls>
                        """)
                .thenReturn("{\"thought\":\"done\",\"action\":\"final\",\"input\":{\"answer\":\"完成\"}}");

        AgentToolExecutor tools = mock(AgentToolExecutor.class);
        when(tools.execute(anyString(), any())).thenReturn(AgentFileTools.ToolResult.ok("file content"));
        when(tools.catalog()).thenReturn("tools");
        AgentSkillsService skills = mock(AgentSkillsService.class);
        when(skills.catalog()).thenReturn("skills");
        AgentRunService runs = mock(AgentRunService.class);
        AgentProperties properties = new AgentProperties();
        AgentReActService service = new AgentReActService(llm, tools, skills, runs, properties, mapper);
        AgentRunService.AgentRun run = new AgentRunService.AgentRun();
        run.setId("test");

        String answer = service.run("读取文件", "无历史", run, event -> { }, 4);
        assertEquals("完成", answer);
        verify(tools).execute(eq("read_file"), any());
    }
}
