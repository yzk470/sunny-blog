package com.sunnyblog.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSearchTest {

    @Test
    void highValueSearchIsGated() {
        AgentProperties properties = new AgentProperties();
        AgentSearchPolicy policy = new AgentSearchPolicy(properties);

        AgentSearchPolicy.Decision normal = policy.decide("计算 2+2", "react");
        assertFalse(normal.highValue());
        assertEquals(36, normal.taskBudget());

        AgentSearchPolicy.Decision complex = policy.decide(
                "请做一次完整的生产架构审计并给出迁移方案", "plan");
        assertTrue(complex.highValue());
        assertEquals(80, complex.taskBudget());
        assertEquals(4, complex.sampleCount());

        properties.setHighValueSearchEnabled(false);
        assertFalse(policy.decide("完整生产架构审计和迁移方案", "plan").highValue());
    }

    @Test
    void budgetControllerReservesAndBoundsAttempts() {
        AgentSearchPolicy policy = new AgentSearchPolicy(new AgentProperties());
        assertTrue(policy.attemptBudget("react", 30, false) <= 12);
        assertTrue(policy.attemptBudget("react", 30, true) <= 14);
        assertTrue(policy.attemptBudget("plan", 30, false) <= 24);
        assertEquals(2, policy.attemptBudget("react", 2, false));
    }

    @Test
    void reflectionExpandsAndReprioritizesBranches() {
        AgentLlmService llm = mock(AgentLlmService.class);
        when(llm.isConfigured()).thenReturn(false);
        AgentSearchService search = new AgentSearchService(llm);
        AgentRunService.Strategy failed = new AgentRunService.Strategy();
        failed.setId("s1");
        failed.setTitle("python-pptx");
        failed.setApproach("直接生成");
        AgentFailureService.Reflection reflection = new AgentFailureService.Reflection(
                "ENVIRONMENT", "python-pptx 可用", "ModuleNotFoundError",
                List.of("不要直接 import pptx"),
                List.of("先检查依赖"),
                List.of("s1"),
                List.of("switch_tool:改用 HTML 生成", "add_preflight:先检查依赖"),
                "所有 PPT 路线必须先做依赖预检", "依赖失败，换路线");
        List<AgentRunService.Strategy> children = search.expand("生成 PPT", failed, reflection, 3, 2);
        assertEquals(2, children.size());
        assertTrue(children.get(0).getApproach().contains("依赖预检"));
        List<AgentSearchService.Scored> rescored = search.rescore("生成 PPT",
                List.of(failed, children.get(0)), reflection);
        assertEquals(0, rescored.stream().filter(s -> s.strategy().getId().equals("s1"))
                .findFirst().orElseThrow().score());
    }

    @Test
    void failureInjectionClassifiesAndReflects() {
        AgentLlmService llm = mock(AgentLlmService.class);
        when(llm.isConfigured()).thenReturn(true);
        when(llm.complete(anyString(), anyString(), anyInt()))
                .thenReturn("下次先检查依赖，再换 HTML 路线。");
        AgentFailureService failures = new AgentFailureService(llm);

        assertEquals("ENVIRONMENT", failures.classify(
                "ModuleNotFoundError: No module named 'pptx'", ""));
        assertEquals("TRANSIENT", failures.classify("request timed out", ""));
        assertEquals("LOGIC", failures.classify("验收未通过：结果不完整", ""));
        assertEquals("POLICY", failures.classify("POLICY_DENIED: 非法路径（只允许 workspace/artifacts/skills）", ""));

        AgentRunService.AttemptRecord attempt = new AgentRunService.AttemptRecord();
        attempt.setStrategyTitle("python-pptx");
        attempt.setApproach("直接用库生成");
        attempt.setAnswer("工具执行失败");
        String reflection = failures.reflect("生成 PPT", attempt, "ENVIRONMENT",
                "ModuleNotFoundError: pptx");
        assertTrue(reflection.contains("依赖") || reflection.contains("HTML"));
    }

    @Test
    void metricsComputeRetryAndSuccessRates() {
        AgentRunService.AgentRun run = new AgentRunService.AgentRun();
        run.setId("metrics-test");
        run.setStatus("DONE");
        AgentRunService.Checkpoint checkpoint = new AgentRunService.Checkpoint();
        checkpoint.setTaskSpent(18);
        checkpoint.setHighValue(true);
        checkpoint.getSearch().setBranchesExpanded(2);
        AgentRunService.AttemptRecord failed = new AgentRunService.AttemptRecord();
        failed.setStatus("FAILED");
        AgentRunService.AttemptRecord success = new AgentRunService.AttemptRecord();
        success.setStatus("SUCCESS");
        checkpoint.getAttempts().add(failed);
        checkpoint.getAttempts().add(success);
        run.setCheckpoint(checkpoint);

        AgentMetricsRepository repository = mock(AgentMetricsRepository.class);
        when(repository.summary(anyInt())).thenReturn(
                new AgentMetricsRepository.Summary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0));
        AgentMetricsService metrics = new AgentMetricsService(repository);
        metrics.record(run);
        AgentMetricsService.Snapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.tasks());
        assertEquals(1, snapshot.successes());
        assertEquals(0.5, snapshot.retryRate());
        assertEquals(18, snapshot.steps());
        assertEquals(1, snapshot.highValueTasks());
        assertEquals(2, snapshot.branchesExpanded());
    }
}
