package com.sunnyblog.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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
    public static class AgentRun {
        private String id;
        private String sessionId;
        private String mode;
        private String question;
        private String status = "RUNNING";
        private String answer;
        private String validation;
        private String createdAt;
        private String updatedAt;
        private List<RunStep> steps = new ArrayList<>();
    }

    public synchronized AgentRun create(String sessionId, String question, String mode) {
        AgentRun run = new AgentRun();
        run.setId(UUID.randomUUID().toString());
        run.setSessionId(sessionId);
        run.setQuestion(question);
        run.setMode(mode);
        run.setCreatedAt(Instant.now().toString());
        run.setUpdatedAt(run.getCreatedAt());
        save(run);
        return run;
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
        try {
            return objectMapper.readValue(file.toFile(), AgentRun.class);
        } catch (IOException ex) {
            return null;
        }
    }

    public List<AgentRun> list() {
        List<AgentRun> runs = new ArrayList<>();
        Path root = runsRoot();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .limit(100)
                    .forEach(path -> {
                        try {
                            runs.add(objectMapper.readValue(path.toFile(), AgentRun.class));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
        runs.sort(Comparator.comparing(AgentRun::getUpdatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return runs;
    }

    private void save(AgentRun run) {
        Path file = runFile(run.getId());
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writeValue(file.toFile(), run);
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
