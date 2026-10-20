package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPlanExecuteTest {

    @Test
    void langGraphPlanExecuteValidatesAndFinishes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentLlmService llm = mock(AgentLlmService.class);
        when(llm.completeJson(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            String system = invocation.getArgument(0);
            if (system.contains("规划器")) {
                return mapper.readTree("{\"steps\":[\"读取需求\",\"生成结果\"],\"reason\":\"拆解\"}");
            }
            return mapper.readTree("{\"passed\":true,\"reason\":\"结果完整\"}");
        });
        AgentReActService react = mock(AgentReActService.class);
        when(react.run(anyString(), anyString(), any(), any(), anyInt())).thenReturn("已生成结果");
        AgentRunService runs = mock(AgentRunService.class);
        AgentProperties properties = new AgentProperties();
        AgentArtifactService artifacts = mock(AgentArtifactService.class);
        when(artifacts.manifestSince(any())).thenReturn("本轮没有新增产物文件。");
        AgentPlanExecuteService service = new AgentPlanExecuteService(llm, react, runs, properties, artifacts);

        AgentRunService.AgentRun run = new AgentRunService.AgentRun();
        run.setId("test");
        AtomicInteger events = new AtomicInteger();
        String result = service.run("完成一个长任务", "无历史", run, event -> events.incrementAndGet(), Instant.now());

        assertEquals("已生成结果", result);
        assertTrue(events.get() > 0);
    }
}
