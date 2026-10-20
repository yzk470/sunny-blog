package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunService {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    @Data
    public static class RunStep {
        private int seq;
        private String type;
        private String title;
        private String detail;
        private boolean success = true;
        private String createdAt;
    }

    @Data
    public static class RunEvent {
        private long seq;
        private String type;
        private String message;
        private String detail;
        private String createdAt;
    }

    @Data
    public static class PendingTool {
        private String action;
        private String input;
    }

    @Data
    public static class ToolRecord {
        private String action;
        private String input;
        private boolean success;
        private String output;
    }

    @Data
    public static class ReActState {
        private int step;
        private String observations = "";
        private boolean executionSucceeded;
        private boolean lastToolFailed;
        private String lastExecutionOutput = "";
        private String lastAnswer;
        private Map<String, Integer> actionCounts = new LinkedHashMap<>();
        private List<ToolRecord> tools = new ArrayList<>();
        private PendingTool pending;
    }

    @Data
    public static class PlanState {
        private List<String> plan = new ArrayList<>();
        private int planIndex;
        private String results = "";
        private String answer = "";
        private int revision;
        private boolean revisionInProgress;
        private ReActState current;
        private String validation = "";
        private boolean passed;
    }

    @Data
    public static class Checkpoint {
        private String phase = "react";
        private ReActState react = new ReActState();
        private PlanState plan = new PlanState();
    }

    @Data
    public static class AgentRun {
        private String id;
        private String sessionId;
        private String clientTaskId;
        private String mode;
        private String question;
        private List<String> knowledgeBaseIds = new ArrayList<>();
        private String status = "RUNNING";
        private String answer;
        private String validation;
        private String createdAt;
        private String updatedAt;
        private long eventSeq;
        private List<RunStep> steps = new ArrayList<>();
        private List<RunEvent> events = new ArrayList<>();
        private Checkpoint checkpoint = new Checkpoint();
    }

    public synchronized AgentRun create(String sessionId, String clientTaskId, String question, String mode) {
        if (clientTaskId != null && !clientTaskId.isBlank()) {
            AgentRun existing = findByClientTaskId(clientTaskId);
            if (existing != null) return existing;
        }
        AgentRun run = new AgentRun();
        run.setId(UUID.randomUUID().toString());
        run.setSessionId(sessionId);
        run.setClientTaskId(clientTaskId);
        run.setQuestion(question);
        run.setMode(mode);
        run.setCreatedAt(Instant.now().toString());
        run.setUpdatedAt(run.getCreatedAt());
        save(run);
        return run;
    }

    public synchronized AgentRun findByClientTaskId(String clientTaskId) {
        if (clientTaskId == null || clientTaskId.isBlank()) return null;
        Path root = runsRoot();
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .map(this::read)
                    .filter(run -> run != null && clientTaskId.equals(run.getClientTaskId()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    public synchronized RunStep addStep(AgentRun run, String type, String title, String detail, boolean success) {
        RunStep step = new RunStep();
        step.setSeq(run.getSteps().size() + 1);
        step.setType(type);
        step.setTitle(title);
        step.setDetail(detail);
        step.setSuccess(success);
        step.setCreatedAt(Instant.now().toString());
        run.getSteps().add(step);
        run.setUpdatedAt(step.getCreatedAt());
        save(run);
        return step;
    }

    public synchronized RunEvent appendEvent(AgentRun run, String type, String message, String detail) {
        long seq = run.getEventSeq() + 1;
        run.setEventSeq(seq);
        RunEvent event = new RunEvent();
        event.setSeq(seq);
        event.setType(type);
        event.setMessage(message);
        event.setDetail(detail);
        event.setCreatedAt(Instant.now().toString());
        run.getEvents().add(event);
        if (run.getEvents().size() > 1000) {
            run.setEvents(new ArrayList<>(run.getEvents().subList(run.getEvents().size() - 1000, run.getEvents().size())));
        }
        run.setUpdatedAt(event.getCreatedAt());
        save(run);
        return event;
    }

    public synchronized void checkpoint(AgentRun run) {
        run.setUpdatedAt(Instant.now().toString());
        save(run);
    }

    public synchronized void setStatus(AgentRun run, String status) {
        run.setStatus(status);
        run.setUpdatedAt(Instant.now().toString());
        save(run);
    }

    public synchronized void finish(AgentRun run, String answer, String status, String validation) {
        run.setAnswer(answer);
        run.setStatus(status);
        run.setValidation(validation);
        run.setUpdatedAt(Instant.now().toString());
        save(run);
    }

    public AgentRun get(String id) {
        Path file = runFile(id);
        if (!Files.exists(file)) return null;
        return read(file);
    }

    public synchronized List<AgentRun> list() {
        List<AgentRun> runs = new ArrayList<>();
        Path root = runsRoot();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .limit(200)
                    .forEach(path -> {
                        AgentRun run = read(path);
                        if (run != null) runs.add(run);
                    });
        } catch (IOException ignored) {
        }
        runs.sort(Comparator.comparing(AgentRun::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return runs;
    }

    private AgentRun read(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), AgentRun.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private void save(AgentRun run) {
        Path file = runFile(run.getId());
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), run.getId(), ".tmp");
            objectMapper.writeValue(temp.toFile(), run);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("保存 Agent 运行记录失败", ex);
        }
    }

    private Path runsRoot() {
        Path root = Path.of(properties.getDir()).toAbsolutePath().normalize().resolve("runs");
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return root;
    }

    private Path runFile(String id) {
        String safe = id == null ? "" : id.replaceAll("[^a-zA-Z0-9\\-]", "");
        return runsRoot().resolve(safe + ".json");
    }
}
