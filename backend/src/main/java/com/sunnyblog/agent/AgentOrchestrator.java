package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AgentLlmService llm;
    private final AgentMemoryService memory;
    private final AgentRunService runs;
    private final AgentReActService react;
    private final AgentPlanExecuteService planExecute;
    private final AgentArtifactService artifacts;
    private final AgentToolExecutor tools;
    private final AgentStrategyService strategies;
    private final AgentPreflightService preflight;
    private final AgentFailureService failures;
    private final AgentSearchPolicy searchPolicy;
    private final AgentSearchService search;
    private final AgentMetricsService metrics;
    private final Map<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public record Result(String sessionId, String runId, String mode, String answer, String validation) {
    }

    public Result chat(String sessionId, String clientTaskId, String question, String requestedMode,
                       List<String> knowledgeBaseIds, Consumer<AgentEvent> events) {
        if (!llm.isConfigured()) {
            throw new IllegalStateException("Agent 模型未配置，请设置 DEEPSEEK_API_KEY");
        }
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        AgentMemoryService.Session session = memory.getOrCreate(sessionId);
        ReentrantLock lock = sessionLocks.computeIfAbsent(session.getId(), key -> new ReentrantLock());
        if (!lock.tryLock()) {
            events.accept(new AgentEvent("error", "当前对话正在执行",
                    "同一个 session 同时只允许一个任务。请等待当前任务完成，或点击“新对话”创建独立任务。"));
            events.accept(new AgentEvent("done", "当前对话忙碌", ""));
            return new Result(session.getId(), "", resolveMode(question, requestedMode),
                    "当前对话正在执行，请等待完成或新建对话。", "");
        }
        try {
            String mode = resolveMode(question, requestedMode);
            AgentRunService.AgentRun run = runs.create(session.getId(), clientTaskId, question, mode);
            run.setKnowledgeBaseIds(knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds));
            if (run.getCheckpoint() == null) run.setCheckpoint(new AgentRunService.Checkpoint());
            if (run.getStatus() == null) run.setStatus("RUNNING");
            runs.checkpoint(run);

            if ("DONE".equals(run.getStatus())) {
                events.accept(new AgentEvent("session", session.getId(), run.getId()));
                events.accept(new AgentEvent("answer", run.getAnswer(), run.getValidation()));
                events.accept(new AgentEvent("done", "完成", run.getId()));
                return new Result(session.getId(), run.getId(), run.getMode(), run.getAnswer(), run.getValidation());
            }
            boolean resumed = hasProgress(run) || "INTERRUPTED".equals(run.getStatus());
            return execute(session, run, mode, question, resumed, events);
        } finally {
            unlock(session.getId(), lock);
        }
    }

    public Result resume(AgentRunService.AgentRun run, Consumer<AgentEvent> events) {
        if (run == null) throw new IllegalArgumentException("任务不存在");
        AgentMemoryService.Session session = memory.getOrCreate(run.getSessionId());
        ReentrantLock lock = sessionLocks.computeIfAbsent(session.getId(), key -> new ReentrantLock());
        if (!lock.tryLock()) {
            events.accept(new AgentEvent("error", "当前对话正在执行",
                    "同一个 session 同时只允许一个任务，请等待当前任务结束后再恢复。"));
            events.accept(new AgentEvent("done", "当前对话忙碌", ""));
            return new Result(session.getId(), run.getId(), run.getMode(), "当前对话正在执行", "");
        }
        try {
            if ("DONE".equals(run.getStatus())) {
                events.accept(new AgentEvent("session", session.getId(), run.getId()));
                events.accept(new AgentEvent("answer", run.getAnswer(), run.getValidation()));
                events.accept(new AgentEvent("done", "完成", run.getId()));
                return new Result(session.getId(), run.getId(), run.getMode(), run.getAnswer(), run.getValidation());
            }
            if (run.getCheckpoint() == null) run.setCheckpoint(new AgentRunService.Checkpoint());
            if (run.getKnowledgeBaseIds() == null) run.setKnowledgeBaseIds(List.of());
            run.setStatus("RUNNING");
            runs.appendEvent(run, "status", "从 checkpoint 恢复任务", "step=" + run.getCheckpoint().getReact().getStep());
            return execute(session, run, run.getMode(), run.getQuestion(), true, events);
        } finally {
            unlock(session.getId(), lock);
        }
    }

    private Result execute(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                           String mode, String question, boolean resumed, Consumer<AgentEvent> events) {
        AgentTaskControl.clear(run.getId());
        AgentTaskContext.set(session.getId(), run.getId());
        tools.setKnowledgeBases(run.getKnowledgeBaseIds());
        try {
            events.accept(new AgentEvent("session", session.getId(), run.getId()));
            events.accept(new AgentEvent("status", resumed ? "从断点继续执行…" : "正在读取记忆与技能…", ""));
            String memoryContext = memory.buildPromptContext(session.getId(), 7000);
            if (!resumed) {
                memory.append(session.getId(), "user", question, memory.classify(question), "question");
            }
            return executeAttempts(session, run, mode, question, memoryContext, events);
        } finally {
            AgentTaskContext.clear();
            tools.clearKnowledgeBases();
        }
    }

    private Result executeAttempts(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                                   String mode, String question, String memoryContext,
                                   Consumer<AgentEvent> events) {
        AgentRunService.Checkpoint checkpoint = run.getCheckpoint();
        if (checkpoint == null) {
            checkpoint = new AgentRunService.Checkpoint();
            run.setCheckpoint(checkpoint);
        }
        if (checkpoint.getSearch() == null) checkpoint.setSearch(new AgentRunService.SearchState());
        if (checkpoint.getStrategies() == null) checkpoint.setStrategies(new ArrayList<>());
        if (checkpoint.getAttempts() == null) checkpoint.setAttempts(new ArrayList<>());
        if (checkpoint.getStrategies().isEmpty()) {
            AgentPreflightService.Preflight check = preflight.check(question);
            runs.addStep(run, "PREFLIGHT", check.summary(), String.join("\n", check.warnings()), check.ok());
            events.accept(new AgentEvent("preflight", check.summary(), String.join("\n", check.warnings())));
            if (!check.ok()) {
                String failed = "预检未通过，未消耗执行预算：" + String.join("；", check.blockers());
                memory.append(session.getId(), "assistant", failed, "MEDIUM", "error");
                runs.finish(run, failed, "FAILED", failed);
                metrics.record(run);
                events.accept(new AgentEvent("error", "预检未通过", failed));
                events.accept(new AgentEvent("done", "预检失败", run.getId()));
                return new Result(session.getId(), run.getId(), mode, failed, failed);
            }
            AgentSearchPolicy.Decision decision = searchPolicy.decide(question, mode);
            AgentStrategyService.StrategyPlan plan = strategies.plan(question, memoryContext, mode,
                    decision.sampleCount());            checkpoint.setSuccessCriteria(plan.successCriteria());
            checkpoint.setStrategies(plan.strategies());
            checkpoint.setTaskBudget(decision.taskBudget());
            checkpoint.setHighValue(decision.highValue());
            checkpoint.getSearch().setEnabled(decision.highValue());
            checkpoint.getSearch().setMode(decision.highValue() ? "BEST_FIRST" : "LINEAR");
            checkpoint.getSearch().setSampleCount(decision.sampleCount());
            checkpoint.getSearch().setMaxBranches(decision.maxBranches());
            checkpoint.getSearch().setBranchesExpanded(0);
            checkpoint.getSearch().getNodes().clear();
            if (decision.highValue()) {
                List<AgentSearchService.Scored> scored = search.rank(question, plan.strategies(), memoryContext);
                List<AgentRunService.Strategy> ranked = new ArrayList<>();
                for (AgentSearchService.Scored item : scored) {
                    AgentRunService.SearchNode node = new AgentRunService.SearchNode();
                    node.setId("node-" + item.strategy().getId());
                    node.setStrategyId(item.strategy().getId());
                    node.setTitle(item.strategy().getTitle());
                    node.setScore(item.score());
                    node.setStatus("PENDING");
                    checkpoint.getSearch().getNodes().add(node);
                    ranked.add(item.strategy());
                }
                int keep = Math.min(decision.maxBranches(), ranked.size());
                checkpoint.setStrategies(new ArrayList<>(ranked.subList(0, keep)));
                events.accept(new AgentEvent("search_start", "高价值任务启用多分支搜索",
                        decision.reason() + "；候选 " + scored.size() + " 条，保留 " + keep + " 条"));
                events.accept(new AgentEvent("branch_score", "路线评分完成",
                        scoreText(checkpoint.getSearch().getNodes())));
            }
            checkpoint.setStrategyIndex(0);
            runs.checkpoint(run);
            events.accept(new AgentEvent("strategy", "生成了 " + checkpoint.getStrategies().size() + " 条候选路线",
                    strategyText(checkpoint.getStrategies())));
        }

        int index = checkpoint.getStrategyIndex();
        AgentRunService.AttemptRecord attempt = null;
        AgentRunService.AttemptRecord last = lastAttempt(checkpoint);
        if (last != null && "SUCCESS".equals(last.getStatus())
                && last.getAnswer() != null && !last.getAnswer().isBlank()) {
            return finishSuccess(session, run, mode, last.getAnswer(),
                    "从已完成尝试恢复", events);
        }
        if (last != null && isResumable(last)) {
            int found = findStrategyIndex(checkpoint, last.getStrategyId());
            if (found >= 0) {
                index = found;
                attempt = last;
                attempt.setStatus("RUNNING");
                checkpoint.setStrategyIndex(index);
                runs.checkpoint(run);
                events.accept(new AgentEvent("attempt_start", "继续尝试 " + (index + 1) + "/"
                        + checkpoint.getStrategies().size(), attempt.getStrategyTitle()));
            }
        } else if (last != null && ("FAILED".equals(last.getStatus()) || "SUCCESS".equals(last.getStatus()))) {
            int found = findStrategyIndex(checkpoint, last.getStrategyId());
            if (found >= 0 && index <= found) {
                index = found + 1;
                checkpoint.setStrategyIndex(index);
                runs.checkpoint(run);
            }
        }

        while (true) {
            if (checkpoint.isHighValue() && attempt == null) {
                index = selectNextIndex(checkpoint);
                if (index < 0) break;
            }
            if (index < 0 || index >= checkpoint.getStrategies().size()) break;
            if (isCancelled(run)) return pauseAttempt(session, run, mode, attempt, events);
            AgentRunService.Strategy strategy = checkpoint.getStrategies().get(index);
            if (attempt == null || !strategy.getId().equals(attempt.getStrategyId())) {
                attempt = newAttempt(strategy);
                checkpoint.getAttempts().add(attempt);
                checkpoint.setStrategyIndex(index);
                checkpoint.setPhase(mode);
                checkpoint.setReact(new AgentRunService.ReActState());
                checkpoint.setPlan(new AgentRunService.PlanState());
                runs.checkpoint(run);
                runs.addStep(run, "ATTEMPT", "尝试路线 " + (index + 1) + "/" + checkpoint.getStrategies().size(),
                        strategy.getTitle() + "：" + strategy.getApproach(), true);
                events.accept(new AgentEvent("attempt_start", "尝试路线 " + (index + 1) + "/"
                        + checkpoint.getStrategies().size(), strategy.getTitle() + "\n" + strategy.getApproach()));
            }
            int remaining = checkpoint.getTaskBudget() - checkpoint.getTaskSpent();
            if (remaining < 4) break;
            int allocation = searchPolicy.attemptBudget(mode, remaining, checkpoint.isHighValue());
            attempt.setBudgetAllocated(allocation);
            attempt.setStatus("RUNNING");
            AgentRunService.SearchNode searchNode = findNode(checkpoint, strategy.getId());
            if (searchNode != null) {
                attempt.setSearchNodeId(searchNode.getId());
                attempt.setSearchScore(searchNode.getScore());
                searchNode.setStatus("RUNNING");
                checkpoint.getSearch().setBranchesExpanded(checkpoint.getSearch().getBranchesExpanded() + 1);
                events.accept(new AgentEvent("branch_select", "选择路线 " + strategy.getTitle(),
                        "score=" + searchNode.getScore()));
            }
            AgentTaskContext.set(session.getId(), run.getId(), attempt.getId());
            String attemptTask = attemptPrompt(question, strategy, checkpoint);
            String answer;
            try {
                if ("plan".equals(mode)) {
                    checkpoint.setPhase("plan");
                    runs.checkpoint(run);
                    answer = planExecute.run(attemptTask, memoryContext, run, events, allocation);
                } else {
                    checkpoint.setPhase("react");
                    runs.checkpoint(run);
                    answer = react.run(attemptTask, memoryContext, run,
                            checkpoint.getReact(), events, allocation);
                }
            } catch (Exception ex) {
                log.warn("Attempt {} failed: {}", attempt.getId(), ex.getMessage(), ex);
                failAttempt(session, run, checkpoint, attempt, strategy, checkpoint.getReact().getTools(),
                        "TOOL", ex.getMessage(), events);
                attempt = null;
                if (checkpoint.isHighValue()) {
                    index = selectNextIndex(checkpoint);
                    if (index < 0) break;
                } else {
                    index++;
                }
                checkpoint.setStrategyIndex(Math.max(0, index));
                continue;
            }

            if (isCancelled(run)) return pauseAttempt(session, run, mode, attempt, events);
            int spent = "plan".equals(mode)
                    ? Math.max(8, checkpoint.getPlan().getPlanIndex() * 8)
                    : Math.max(1, checkpoint.getReact().getStep());
            int budgetDelta = Math.max(0, spent - attempt.getBudgetSpent());
            attempt.setBudgetSpent(Math.max(attempt.getBudgetSpent(), spent));
            checkpoint.setTaskSpent(checkpoint.getTaskSpent() + budgetDelta);
            attempt.setAnswer(answer);
            attempt.setArtifacts(artifactPaths(run, attempt));
            attempt.setSuccessfulTools(countTools(checkpoint, true));
            attempt.setFailedTools(countTools(checkpoint, false));
            attempt.setExecutionEvidence(executionEvidence(checkpoint));
            runs.checkpoint(run);

            Verdict verdict = verifyAttempt(question, run, checkpoint, attempt, answer);
            if (verdict.passed()) {
                attempt.setStatus("SUCCESS");
                attempt.setEndedAt(java.time.Instant.now().toString());
                strategy.setStatus("SUCCESS");
                AgentRunService.SearchNode successNode = findNode(checkpoint, strategy.getId());
                if (successNode != null) successNode.setStatus("SUCCESS");
                checkpoint.setBestAnswer(answer);
                checkpoint.setBestAttemptId(attempt.getId());
                checkpoint.setStrategyIndex(index + 1);
                runs.checkpoint(run);
                runs.addStep(run, "ATTEMPT", "路线通过", attempt.getStrategyTitle(), true);
                events.accept(new AgentEvent("attempt_success", "路线 " + (index + 1) + " 通过",
                        attempt.getStrategyTitle() + "：" + verdict.reason()));
                return finishSuccess(session, run, mode, answer, verdict.reason(), events);
            }

            failAttempt(session, run, checkpoint, attempt, strategy, checkpoint.getReact().getTools(),
                    verdict.failureType(), verdict.reason(), events);
            attempt = null;
            if (checkpoint.isHighValue()) {
                index = selectNextIndex(checkpoint);
                if (index < 0) break;
            } else {
                index++;
            }
            checkpoint.setStrategyIndex(Math.max(0, index));
        }

        return finishBestEffort(session, run, mode, checkpoint, events);
    }

    private AgentRunService.AttemptRecord newAttempt(AgentRunService.Strategy strategy) {
        AgentRunService.AttemptRecord attempt = new AgentRunService.AttemptRecord();
        attempt.setId("att-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        attempt.setStrategyId(strategy.getId());
        attempt.setStrategyTitle(strategy.getTitle());
        attempt.setApproach(strategy.getApproach());
        attempt.setStatus("RUNNING");
        attempt.setStartedAt(java.time.Instant.now().toString());
        return attempt;
    }

    private AgentRunService.AttemptRecord lastAttempt(AgentRunService.Checkpoint checkpoint) {
        return checkpoint.getAttempts().isEmpty()
                ? null : checkpoint.getAttempts().get(checkpoint.getAttempts().size() - 1);
    }

    private boolean isResumable(AgentRunService.AttemptRecord attempt) {
        return "RUNNING".equals(attempt.getStatus()) || "PAUSED".equals(attempt.getStatus());
    }

    private int findStrategyIndex(AgentRunService.Checkpoint checkpoint, String strategyId) {
        for (int i = 0; i < checkpoint.getStrategies().size(); i++) {
            if (checkpoint.getStrategies().get(i).getId().equals(strategyId)) return i;
        }
        return -1;
    }

    private void failAttempt(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                             AgentRunService.Checkpoint checkpoint, AgentRunService.AttemptRecord attempt,
                             AgentRunService.Strategy strategy, List<AgentRunService.ToolRecord> tools,
                             String failureType, String reason, Consumer<AgentEvent> events) {
        attempt.setStatus("FAILED");
        attempt.setEndedAt(java.time.Instant.now().toString());
        attempt.setFailureType(failureType);
        attempt.setFailureReason(reason);
        attempt.setArtifacts(artifactPaths(run, attempt));
        attempt.setSuccessfulTools(countTools(checkpoint, true));
        attempt.setFailedTools(countTools(checkpoint, false));
        attempt.setExecutionEvidence(executionEvidence(checkpoint));
        AgentFailureService.Reflection reflection = failures.reflectStructured(
                run.getQuestion(), attempt, failureType, reason, strategy);
        attempt.setReflection(reflection.summary());
        attempt.setFailedAssumption(reflection.failedAssumption());
        attempt.setFailureEvidence(reflection.evidence());
        attempt.setGlobalConstraint(reflection.globalConstraint());
        attempt.setForbiddenActions(reflection.forbiddenActions());
        attempt.setRequiredPreconditions(reflection.requiredPreconditions());
        attempt.setInvalidatesStrategyIds(reflection.invalidatesStrategyIds());
        attempt.setRepairOperators(reflection.repairOperators());
        strategy.setStatus("FAILED");
        AgentRunService.SearchNode failedNode = findNode(checkpoint, strategy.getId());
        if (failedNode != null) {
            failedNode.setStatus("FAILED");
            failedNode.setFailureType(failureType);
            failedNode.setReflection(reflection.summary());
        }
        if (checkpoint.isHighValue()) {
            applyReflection(run, checkpoint, strategy, failedNode, reflection, events);
        }
        runs.checkpoint(run);
        runs.addStep(run, "ATTEMPT", "路线失败", attempt.getStrategyTitle() + "：" + reason, false);
        events.accept(new AgentEvent("attempt_failed", "路线 " + attempt.getStrategyTitle() + " 失败",
                failureType + "：" + reason));
        events.accept(new AgentEvent("reflection", "下次尝试的结构化教训",
                reflection.summary() + "\n约束：" + reflection.globalConstraint()));
        checkpoint.setReact(new AgentRunService.ReActState());
        checkpoint.setPlan(new AgentRunService.PlanState());
        runs.checkpoint(run);
    }

    private void applyReflection(AgentRunService.AgentRun run, AgentRunService.Checkpoint checkpoint,
                                 AgentRunService.Strategy failedStrategy,
                                 AgentRunService.SearchNode failedNode,
                                 AgentFailureService.Reflection reflection,
                                 Consumer<AgentEvent> events) {
        if (reflection.globalConstraint() != null && !reflection.globalConstraint().isBlank()
                && !checkpoint.getSearch().getGlobalConstraints().contains(reflection.globalConstraint())) {
            checkpoint.getSearch().getGlobalConstraints().add(reflection.globalConstraint());
        }
        int pruned = 0;
        for (AgentRunService.SearchNode node : checkpoint.getSearch().getNodes()) {
            if (!"PENDING".equals(node.getStatus())) continue;
            if (reflection.invalidatesStrategyIds() != null
                    && reflection.invalidatesStrategyIds().contains(node.getStrategyId())) {
                node.setStatus("PRUNED");
                for (AgentRunService.Strategy strategy : checkpoint.getStrategies()) {
                    if (strategy.getId().equals(node.getStrategyId())) strategy.setStatus("PRUNED");
                }
                pruned++;
            }
        }
        int parentDepth = failedNode == null ? 0 : failedNode.getDepth();
        boolean canExpand = parentDepth < checkpoint.getSearch().getMaxDepth()
                && checkpoint.getSearch().getNodes().size() < checkpoint.getSearch().getMaxNodes();
        if (canExpand) {
            List<AgentRunService.Strategy> children = search.expand(run.getQuestion(), failedStrategy,
                    reflection, checkpoint.getStrategies().size(), 2);
            for (AgentRunService.Strategy child : children) {
                if (checkpoint.getSearch().getNodes().size() >= checkpoint.getSearch().getMaxNodes()) break;
                checkpoint.getStrategies().add(child);
                AgentRunService.SearchNode node = new AgentRunService.SearchNode();
                node.setId("node-" + child.getId());
                node.setStrategyId(child.getId());
                node.setParentId(failedNode == null ? null : failedNode.getId());
                node.setTitle(child.getTitle());
                node.setScore(60);
                node.setDepth(parentDepth + 1);
                node.setStatus("PENDING");
                checkpoint.getSearch().getNodes().add(node);
                if (failedNode != null) failedNode.getChildren().add(node.getId());
            }
            if (!children.isEmpty()) {
                events.accept(new AgentEvent("branch_expand",
                        "反思生成了 " + children.size() + " 个修复分支",
                        children.stream().map(AgentRunService.Strategy::getTitle).toList().toString()));
            }
        }
        List<AgentRunService.Strategy> pending = checkpoint.getStrategies().stream()
                .filter(s -> "PENDING".equals(s.getStatus())).toList();
        if (!pending.isEmpty()) {
            List<AgentSearchService.Scored> rescored = search.rescore(run.getQuestion(), pending, reflection);
            for (AgentSearchService.Scored item : rescored) {
                AgentRunService.SearchNode node = findNode(checkpoint, item.strategy().getId());
                if (node != null && "PENDING".equals(node.getStatus())) {
                    node.setScore(item.score());
                }
            }
            events.accept(new AgentEvent("branch_rescore", "基于失败反思重新评分",
                    rescored.stream().map(s -> s.strategy().getTitle() + "：" + s.score())
                            .toList().toString()));
        }
        if (pruned > 0) {
            events.accept(new AgentEvent("branch_pruned", "剪掉了 " + pruned + " 条被失败反思否定的路线",
                    reflection.summary()));
        }
    }

    private Result pauseAttempt(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                                String mode, AgentRunService.AttemptRecord attempt,
                                Consumer<AgentEvent> events) {
        if (attempt != null) {
            AgentRunService.Checkpoint checkpoint = run.getCheckpoint();
            int spent = "plan".equals(mode)
                    ? Math.max(8, checkpoint.getPlan().getPlanIndex() * 8)
                    : Math.max(1, checkpoint.getReact().getStep());
            int delta = Math.max(0, spent - attempt.getBudgetSpent());
            attempt.setBudgetSpent(Math.max(attempt.getBudgetSpent(), spent));
            checkpoint.setTaskSpent(checkpoint.getTaskSpent() + delta);
            attempt.setStatus("PAUSED");
            runs.checkpoint(run);
        }
        String paused = "任务已按请求暂停，当前尝试进度已写入 checkpoint。";
        runs.finish(run, paused, "PAUSED", "PAUSED");
        events.accept(new AgentEvent("answer", paused, "已暂停，可恢复"));
        events.accept(new AgentEvent("done", "已暂停", run.getId()));
        return new Result(session.getId(), run.getId(), mode, paused, "PAUSED");
    }

    private Result finishSuccess(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                                 String mode, String answer, String validation,
                                 Consumer<AgentEvent> events) {
        List<AgentArtifactService.Artifact> produced = artifacts.listRun(run.getId());
        events.accept(new AgentEvent("artifacts",
                produced.isEmpty() ? "本轮没有生成文件产物" : "生成了 " + produced.size() + " 个文件",
                artifacts.json(produced)));
        memory.append(session.getId(), "assistant", answer, "MEDIUM", "answer");
        runs.finish(run, answer, "DONE", validation);
        metrics.record(run);
        events.accept(new AgentEvent("answer", answer, validation));
        events.accept(new AgentEvent("done", "完成", run.getId()));
        return new Result(session.getId(), run.getId(), mode, answer, validation);
    }

    private Result finishBestEffort(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                                    String mode, AgentRunService.Checkpoint checkpoint,
                                    Consumer<AgentEvent> events) {
        AgentRunService.AttemptRecord best = null;
        for (AgentRunService.AttemptRecord attempt : checkpoint.getAttempts()) {
            if (attempt.getAnswer() == null || attempt.getAnswer().isBlank()) continue;
            if (best == null || attempt.getAnswer().length() > best.getAnswer().length()) best = attempt;
        }
        String answer = best != null ? best.getAnswer()
                : "已尝试 " + checkpoint.getAttempts().size() + " 条路线，但没有得到可用结果。";
        String reason = "已尝试 " + checkpoint.getAttempts().size() + " 条路线，全部未通过完整验收";
        checkpoint.setBestAnswer(answer);
        if (best != null) checkpoint.setBestAttemptId(best.getId());
        runs.checkpoint(run);
        List<AgentArtifactService.Artifact> produced = artifacts.listRun(run.getId());
        events.accept(new AgentEvent("artifacts",
                produced.isEmpty() ? "本轮没有生成文件产物" : "生成了 " + produced.size() + " 个文件",
                artifacts.json(produced)));
        memory.append(session.getId(), "assistant", answer, "MEDIUM", "answer");
        runs.finish(run, answer, "DONE", reason);
        metrics.record(run);
        events.accept(new AgentEvent("validation", "多路线尝试结束", reason));
        events.accept(new AgentEvent("answer", answer, reason));
        events.accept(new AgentEvent("done", "完成（部分）", run.getId()));
        return new Result(session.getId(), run.getId(), mode, answer, reason);
    }

    private String attemptPrompt(String question, AgentRunService.Strategy strategy,
                                 AgentRunService.Checkpoint checkpoint) {
        StringBuilder previous = new StringBuilder();
        for (AgentRunService.AttemptRecord prior : checkpoint.getAttempts()) {
            if ("FAILED".equals(prior.getStatus()) && prior.getReflection() != null) {
                previous.append("- ").append(prior.getStrategyTitle()).append("：")
                        .append(prior.getReflection());
                if (prior.getForbiddenActions() != null && !prior.getForbiddenActions().isEmpty()) {
                    previous.append("；禁止：").append(String.join("、", prior.getForbiddenActions()));
                }
                if (prior.getRequiredPreconditions() != null && !prior.getRequiredPreconditions().isEmpty()) {
                    previous.append("；前置：").append(String.join("、", prior.getRequiredPreconditions()));
                }
                previous.append("\n");
            }
        }
        if (checkpoint.getSearch().getGlobalConstraints() != null) {
            for (String constraint : checkpoint.getSearch().getGlobalConstraints()) {
                previous.append("- 全局约束：").append(constraint).append("\n");
            }
        }
        return """
                原始任务：%s

                本次只执行这一条候选路线：
                路线：%s
                做法：%s
                已知风险：%s
                备选：%s

                成功标准：%s

                之前失败的教训（不要重复）：
                %s

                请认真执行当前路线。如果当前路线被验证为不可行，请明确说明失败原因，
                系统会切换到下一条路线，而不是无限重复同一动作。
                """.formatted(question, strategy.getTitle(), strategy.getApproach(),
                strategy.getRisk(), strategy.getFallback(), checkpoint.getSuccessCriteria(),
                previous.length() == 0 ? "（无）" : previous.toString());
    }

    private List<String> artifactPaths(AgentRunService.AgentRun run, AgentRunService.AttemptRecord attempt) {
        String prefix = run.getId() + "/attempts/" + attempt.getId() + "/";
        return artifacts.listRun(run.getId()).stream()
                .map(AgentArtifactService.Artifact::path)
                .filter(path -> path.startsWith(prefix))
                .toList();
    }

    private int countTools(AgentRunService.Checkpoint checkpoint, boolean success) {
        return (int) checkpoint.getReact().getTools().stream()
                .filter(tool -> tool.isSuccess() == success).count();
    }

    private boolean expectsArtifact(String task) {
        return task != null && task.matches("(?is).*(生成|创建|制作|导出|输出|写|做).*(pptx?|docx?|word|xlsx?|excel|pdf|html|图片|插画|海报|演示文稿|文档).*");
    }

    private boolean expectsExecution(String task) {
        return task != null && task.matches("(?is).*(脚本|运行|执行|python|node|命令|转换|爬取|抓取).*");
    }

    private Verdict verifyAttempt(String question, AgentRunService.AgentRun run,
                                  AgentRunService.Checkpoint checkpoint,
                                  AgentRunService.AttemptRecord attempt, String answer) {
        if (answer == null || answer.isBlank()) {
            return new Verdict(false, "没有生成回答", "LOGIC");
        }
        if (answer.contains("步数已达到上限") || answer.contains("没有生成有效回答")) {
            return new Verdict(false, "执行达到预算上限", "BUDGET");
        }
        List<String> produced = attempt.getArtifacts() == null ? List.of() : attempt.getArtifacts();
        if (expectsArtifact(question) && produced.isEmpty()) {
            return new Verdict(false, "任务要求文件产物，但当前路线没有生成文件", "ENVIRONMENT");
        }
        if (expectsExecution(question) && "react".equals(run.getMode())
                && checkpoint.getReact().getTools().stream().noneMatch(tool ->
                tool.isSuccess() && ("run_script".equals(tool.getAction()) || "run_command".equals(tool.getAction())))) {
            return new Verdict(false, "任务要求真实执行，但当前路线没有成功运行脚本或命令", "TOOL");
        }
        Validation validation = validate(question, answer,
                artifacts.manifestRun(run.getId()) + executionEvidenceBlock(attempt),
                checkpoint.getSuccessCriteria());
        return new Verdict(validation.passed(), validation.reason(),
                validation.passed() ? "" : "LOGIC");
    }

    private String executionEvidence(AgentRunService.Checkpoint checkpoint) {
        if (checkpoint.getReact() == null || checkpoint.getReact().getTools() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (AgentRunService.ToolRecord tool : checkpoint.getReact().getTools()) {
            if (tool.isSuccess() && ("run_script".equals(tool.getAction()) || "run_command".equals(tool.getAction()))) {
                sb.append("- ").append(tool.getAction()).append("：").append(clip(tool.getOutput(), 1500)).append("\n");
            }
        }
        return sb.toString();
    }

    private String executionEvidenceBlock(AgentRunService.AttemptRecord attempt) {
        if (attempt.getExecutionEvidence() == null || attempt.getExecutionEvidence().isBlank()) return "";
        return "\n\n执行证据（脚本在 workspace，不属于 artifacts；成功执行输出的真实结果）：\n"
                + attempt.getExecutionEvidence();
    }

    private String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "\n...[已截断]";
    }

    private String strategyText(List<AgentRunService.Strategy> strategies) {
        StringBuilder sb = new StringBuilder();
        for (AgentRunService.Strategy strategy : strategies) {
            sb.append(strategy.getId()).append(". ").append(strategy.getTitle())
                    .append("：").append(strategy.getApproach()).append("\n");
        }
        return sb.toString();
    }

    private AgentRunService.SearchNode findNode(AgentRunService.Checkpoint checkpoint, String strategyId) {
        if (checkpoint.getSearch() == null || checkpoint.getSearch().getNodes() == null) return null;
        for (AgentRunService.SearchNode node : checkpoint.getSearch().getNodes()) {
            if (strategyId.equals(node.getStrategyId())) return node;
        }
        return null;
    }

    private int selectNextIndex(AgentRunService.Checkpoint checkpoint) {
        AgentRunService.SearchNode best = null;
        for (AgentRunService.SearchNode node : checkpoint.getSearch().getNodes()) {
            if (!"PENDING".equals(node.getStatus())) continue;
            if (best == null || node.getScore() > best.getScore()) best = node;
        }
        if (best == null) return -1;
        int bestIndex = -1;
        for (int i = 0; i < checkpoint.getStrategies().size(); i++) {
            AgentRunService.Strategy strategy = checkpoint.getStrategies().get(i);
            if (strategy.getId().equals(best.getStrategyId()) && "PENDING".equals(strategy.getStatus())) {
                bestIndex = i;
                break;
            }
        }
        return bestIndex;
    }

    private String scoreText(List<AgentRunService.SearchNode> nodes) {
        StringBuilder sb = new StringBuilder();
        for (AgentRunService.SearchNode node : nodes) {
            sb.append(node.getTitle()).append("：").append(node.getScore()).append("\n");
        }
        return sb.toString();
    }

    private record Verdict(boolean passed, String reason, String failureType) {
    }

    private boolean hasProgress(AgentRunService.AgentRun run) {
        AgentRunService.Checkpoint cp = run.getCheckpoint();
        if (cp == null) return false;
        if (cp.getAttempts() != null && !cp.getAttempts().isEmpty()) return true;
        if ("plan".equals(cp.getPhase())) {
            return cp.getPlan() != null && (cp.getPlan().getPlanIndex() > 0 || cp.getPlan().getCurrent() != null);
        }
        return cp.getReact() != null && (cp.getReact().getStep() > 0 || cp.getReact().getLastAnswer() != null);
    }

    private void unlock(String sessionId, ReentrantLock lock) {
        lock.unlock();
        if (!lock.isLocked() && !lock.hasQueuedThreads()) {
            sessionLocks.remove(sessionId, lock);
        }
    }

    public Validation validate(String task, String answer, String artifactManifest) {
        return validate(task, answer, artifactManifest, "");
    }

    public Validation validate(String task, String answer, String artifactManifest, String successCriteria) {
        String system = """
                你是独立验收员。你只能看到用户任务、候选结果、产物目录清单和本任务的成功标准，看不到执行者的思考过程。
                判断结果是否真正完成任务；如果只是声称做了、空泛、遗漏关键产物、或答非所问，就不通过。
                如果任务要求生成文件（pptx/docx/xlsx/pdf/html/图片等），产物清单中必须有对应文件；
                只有文字说明而没有产物时，不能通过。
                如果任务只是要求写脚本并运行，执行证据中有成功的 run_script/run_command 和真实输出，
                且候选结果给出脚本路径与结果，即使脚本在 workspace 而不在 artifacts，也可以通过；
                只有用户明确要求交付脚本文件时才要求 artifacts 中有脚本。
                只输出 JSON：{"passed":true/false,"reason":"一句中文理由"}。
                """;
        String user = "用户任务：\n" + task + "\n\n成功标准：\n"
                + (successCriteria == null || successCriteria.isBlank() ? "产物真实可用，回答直接解决问题" : successCriteria)
                + "\n\n候选结果：\n" + answer
                + "\n\n产物清单：\n" + (artifactManifest == null ? "无" : artifactManifest);
        try {
            JsonNode json = llm.completeJson(system, user, 45);
            boolean passed = json.path("passed").asBoolean(false);
            String reason = json.path("reason").asText(passed ? "任务已完成" : "需要修正");
            return new Validation(passed, reason);
        } catch (Exception ex) {
            log.warn("Agent validation skipped: {}", ex.getMessage());
            return new Validation(true, "验收模型暂不可用，跳过二次验收");
        }
    }

    public record Validation(boolean passed, String reason) {
    }

    private boolean isCancelled(AgentRunService.AgentRun run) {
        return "CANCEL_REQUESTED".equals(run.getStatus()) || AgentTaskControl.isCancelled(run.getId());
    }

    private Result pause(AgentMemoryService.Session session, AgentRunService.AgentRun run,
                         String mode, String validation, Consumer<AgentEvent> events) {
        String cancelled = "任务已按请求停止，可以稍后从 checkpoint 继续。";
        runs.finish(run, cancelled, "PAUSED", validation);
        events.accept(new AgentEvent("answer", cancelled, "已暂停，可恢复"));
        events.accept(new AgentEvent("done", "已暂停", run.getId()));
        return new Result(session.getId(), run.getId(), mode, cancelled, validation);
    }

    private String ensureAnswer(String answer, String runId) {
        if (answer != null && !answer.isBlank()
                && !answer.contains("步数已达到上限")
                && !answer.contains("没有生成有效回答")) {
            return answer;
        }
        List<AgentArtifactService.Artifact> produced = artifacts.listRun(runId);
        if (produced.isEmpty()) return answer;
        StringBuilder sb = new StringBuilder("任务已完成，并生成了这些产物：\n");
        for (AgentArtifactService.Artifact artifact : produced) {
            sb.append("- ").append(artifact.name()).append("（").append(artifact.type())
                    .append("，").append(artifact.size()).append(" bytes）\n");
        }
        return sb.toString().trim();
    }

    private String resolveMode(String question, String requested) {
        if ("plan".equalsIgnoreCase(requested)) return "plan";
        if ("react".equalsIgnoreCase(requested)) return "react";
        String text = question.toLowerCase();
        boolean longTask = question.length() > 110
                || text.matches("(?s).*(开发|实现|搭建|项目|多步骤|完整方案|调研报告|生成一个|写一个.*(网站|系统|工具)|先.*然后|批量|脚本文件).*");
        return longTask ? "plan" : "react";
    }
}
