package com.sunnyblog.agent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Owns the execution channel independently from the HTTP connection.
 * Closing a browser tab only detaches a subscriber; it never stops the task.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentOrchestrator orchestrator;
    private final AgentRunService runs;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, TaskChannel> active = new ConcurrentHashMap<>();
    private final Map<String, TaskChannel> byClientTask = new ConcurrentHashMap<>();

    private static final class TaskChannel {
        private final String clientTaskId;
        private volatile String runId;
        private volatile boolean done;
        private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
        private final List<AgentEvent> early = new CopyOnWriteArrayList<>();

        private TaskChannel(String clientTaskId) {
            this.clientTaskId = clientTaskId;
        }
    }

    public record TaskView(String taskId, String sessionId, String clientTaskId, String status,
                           String question, String mode, String answer, String validation,
                           long lastEventSeq, boolean active,
                           int attemptCount, int taskBudget, int taskSpent, boolean highValue,
                           List<AgentRunService.RunEvent> events) {
    }

    @PostConstruct
    public void recover() {
        for (AgentRunService.AgentRun run : runs.list()) {
            if ("RUNNING".equals(run.getStatus())) {
                runs.appendEvent(run, "status", "服务重启，任务已中断", "可以从 checkpoint 继续执行");
                runs.setStatus(run, "INTERRUPTED");
            }
        }
        heartbeat.scheduleAtFixedRate(() -> {
            for (TaskChannel channel : active.values()) {
                if (channel.done) continue;
                for (SseEmitter emitter : channel.subscribers) {
                    if (!send(emitter, null, new AgentEvent("heartbeat", "", ""))) {
                        channel.subscribers.remove(emitter);
                    }
                }
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        heartbeat.shutdownNow();
    }

    public TaskView byClientTaskId(String clientTaskId) {
        AgentRunService.AgentRun run = runs.findByClientTaskId(clientTaskId);
        return run == null ? null : view(run.getId());
    }

    public synchronized SseEmitter start(String sessionId, String clientTaskId, String question,
                            String mode, List<String> knowledgeBaseIds, String benchmarkVariant) {
        String clientId = clientTaskId == null || clientTaskId.isBlank()
                ? UUID.randomUUID().toString() : clientTaskId.trim();
        TaskChannel existingChannel = byClientTask.get(clientId);
        if (existingChannel != null && !existingChannel.done) {
            if (existingChannel.runId != null) return attach(existingChannel.runId, 0);
            SseEmitter joined = createEmitter(existingChannel);
            existingChannel.subscribers.add(joined);
            return joined;
        }
        AgentRunService.AgentRun existing = runs.findByClientTaskId(clientId);
        if (existing != null) {
            return attach(existing.getId(), 0);
        }
        TaskChannel channel = new TaskChannel(clientId);
        byClientTask.put(clientId, channel);
        SseEmitter emitter = createEmitter(channel);
        channel.subscribers.add(emitter);
        executor.submit(() -> execute(channel, sessionId, question, mode, knowledgeBaseIds, benchmarkVariant));
        return emitter;
    }

    public SseEmitter resume(String taskId) {
        AgentRunService.AgentRun run = runs.get(taskId);
        if (run == null) {
            SseEmitter emitter = new SseEmitter(30_000L);
            send(emitter, null, new AgentEvent("error", "任务不存在", taskId));
            emitter.complete();
            return emitter;
        }
        TaskChannel activeChannel = active.get(taskId);
        if (activeChannel != null && !activeChannel.done) {
            return attach(taskId, 0);
        }
        if ("DONE".equals(run.getStatus())) {
            return attach(taskId, 0);
        }
        TaskChannel channel = new TaskChannel(run.getClientTaskId());
        channel.runId = taskId;
        AgentTaskControl.clear(taskId);
        active.put(taskId, channel);
        if (channel.clientTaskId != null) byClientTask.put(channel.clientTaskId, channel);
        SseEmitter emitter = createEmitter(channel);
        channel.subscribers.add(emitter);
        executor.submit(() -> resumeExecution(channel, run));
        return emitter;
    }

    public SseEmitter attach(String taskId, long afterSeq) {
        AgentRunService.AgentRun run = runs.get(taskId);
        if (run == null) {
            SseEmitter emitter = new SseEmitter(30_000L);
            send(emitter, null, new AgentEvent("error", "任务不存在", taskId));
            emitter.complete();
            return emitter;
        }
        TaskChannel channel = active.get(taskId);
        boolean live = channel != null && !channel.done;
        if (!live) {
            channel = new TaskChannel(run.getClientTaskId());
            channel.runId = taskId;
        }
        SseEmitter emitter = createEmitter(channel);
        synchronized (channel) {
            AgentRunService.AgentRun fresh = runs.get(taskId);
            if (fresh != null) run = fresh;
            try {
                for (AgentRunService.RunEvent event : run.getEvents()) {
                    if (event.getSeq() > afterSeq) {
                        send(emitter, event.getSeq(), new AgentEvent(event.getType(), event.getMessage(), event.getDetail()));
                    }
                }
            } catch (Exception ex) {
                log.debug("Agent replay failed: {}", ex.getMessage());
            }
            if (live) {
                channel.subscribers.add(emitter);
            } else {
                if (!containsDone(run, afterSeq)) {
                    String message = "RUNNING".equals(run.getStatus())
                            ? "任务已中断，可从 checkpoint 恢复" : "任务已结束";
                    send(emitter, run.getEventSeq(), new AgentEvent("done", message, taskId));
                }
                emitter.complete();
            }
        }
        return emitter;
    }

    public TaskView view(String taskId) {
        AgentRunService.AgentRun run = runs.get(taskId);
        if (run == null) return null;
        TaskChannel channel = active.get(taskId);
        AgentRunService.Checkpoint checkpoint = run.getCheckpoint();
        return new TaskView(run.getId(), run.getSessionId(), run.getClientTaskId(), run.getStatus(),
                run.getQuestion(), run.getMode(), run.getAnswer(), run.getValidation(),
                run.getEventSeq(), channel != null && !channel.done,
                checkpoint == null || checkpoint.getAttempts() == null ? 0 : checkpoint.getAttempts().size(),
                checkpoint == null ? 0 : checkpoint.getTaskBudget(),
                checkpoint == null ? 0 : checkpoint.getTaskSpent(),
                checkpoint != null && checkpoint.isHighValue(),
                run.getEvents());
    }

    public List<AgentRunService.RunEvent> events(String taskId, long afterSeq) {
        AgentRunService.AgentRun run = runs.get(taskId);
        if (run == null) return List.of();
        List<AgentRunService.RunEvent> result = new ArrayList<>();
        for (AgentRunService.RunEvent event : run.getEvents()) {
            if (event.getSeq() > afterSeq) result.add(event);
        }
        return result;
    }

    public boolean cancel(String taskId) {
        AgentRunService.AgentRun run = runs.get(taskId);
        if (run == null || !isRunning(run)) return false;
        AgentTaskControl.cancel(taskId);
        run.setStatus("CANCEL_REQUESTED");
        runs.checkpoint(run);
        return true;
    }

    private void execute(TaskChannel channel, String sessionId, String question,
                         String mode, List<String> knowledgeBaseIds, String benchmarkVariant) {
        try {
            orchestrator.chat(sessionId, channel.clientTaskId, question, mode, knowledgeBaseIds, benchmarkVariant,
                    event -> publish(channel, event));
        } catch (Exception ex) {
            log.warn("Agent task failed: {}", ex.getMessage(), ex);
            publish(channel, new AgentEvent("error", "执行失败", ex.getMessage()));
            if (channel.runId != null) {
                AgentRunService.AgentRun run = runs.get(channel.runId);
                if (run != null) {
                    runs.appendEvent(run, "error", "执行失败", ex.getMessage());
                    runs.finish(run, "任务执行失败：" + ex.getMessage(), "FAILED", "");
                }
            }
        } finally {
            finishChannel(channel);
        }
    }

    private void resumeExecution(TaskChannel channel, AgentRunService.AgentRun run) {
        try {
            orchestrator.resume(run, event -> publish(channel, event));
        } catch (Exception ex) {
            log.warn("Agent resume failed: {}", ex.getMessage(), ex);
            publish(channel, new AgentEvent("error", "恢复失败", ex.getMessage()));
            runs.appendEvent(run, "error", "恢复失败", ex.getMessage());
            runs.finish(run, "任务恢复失败：" + ex.getMessage(), "FAILED", run.getValidation());
        } finally {
            finishChannel(channel);
        }
    }

    private synchronized void publish(TaskChannel channel, AgentEvent event) {
        if ("session".equals(event.type()) && channel.runId == null) {
            channel.runId = event.detail();
            active.put(channel.runId, channel);
        }
        if (channel.runId == null) {
            channel.early.add(event);
            for (SseEmitter emitter : channel.subscribers) {
                send(emitter, null, event);
            }
            return;
        }
        AgentRunService.AgentRun run = runs.get(channel.runId);
        if (run == null) return;
        AgentRunService.RunEvent saved = runs.appendEvent(run, event.type(), event.message(), event.detail());
        for (SseEmitter emitter : channel.subscribers) {
            if (!send(emitter, saved.getSeq(), event)) {
                channel.subscribers.remove(emitter);
            }
        }
    }

    private void finishChannel(TaskChannel channel) {
        channel.done = true;
        if (channel.runId != null) {
            active.remove(channel.runId, channel);
            AgentRunService.AgentRun run = runs.get(channel.runId);
            if (run != null && "RUNNING".equals(run.getStatus())) {
                runs.finish(run, run.getAnswer(), "DONE", run.getValidation());
            }
        }
        if (channel.clientTaskId != null) byClientTask.remove(channel.clientTaskId, channel);
        for (SseEmitter emitter : channel.subscribers) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
        channel.subscribers.clear();
    }

    private SseEmitter createEmitter(TaskChannel channel) {
        SseEmitter emitter = new SseEmitter(600_000L);
        emitter.onTimeout(() -> {
            channel.subscribers.remove(emitter);
            emitter.complete();
        });
        emitter.onError(ex -> channel.subscribers.remove(emitter));
        emitter.onCompletion(() -> channel.subscribers.remove(emitter));
        return emitter;
    }

    private boolean send(SseEmitter emitter, Long seq, AgentEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", event.message());
            payload.put("detail", event.detail());
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(event.type()).data(payload);
            if (seq != null) builder.id(String.valueOf(seq));
            emitter.send(builder);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isRunning(AgentRunService.AgentRun run) {
        return "RUNNING".equals(run.getStatus()) || "INTERRUPTED".equals(run.getStatus())
                || "PAUSED".equals(run.getStatus());
    }

    private boolean containsDone(AgentRunService.AgentRun run, long afterSeq) {
        for (AgentRunService.RunEvent event : run.getEvents()) {
            if (event.getSeq() > afterSeq && "done".equals(event.getType())) return true;
        }
        return false;
    }
}
