package com.sunnyblog.agent;

import com.sunnyblog.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentTaskService tasks;
    private final AgentMemoryService memory;
    private final AgentSkillsService skills;
    private final AgentLlmService llm;
    private final AgentProperties properties;
    private final AgentArtifactService artifacts;

    public record ChatRequest(String sessionId, String clientTaskId, String message,
                              String mode, List<String> knowledgeBaseIds, String benchmarkVariant) {
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "configured", llm.isConfigured(),
                "executionEnabled", properties.isExecutionEnabled(),
                "model", properties.getModel()
        ));
    }

    @GetMapping("/skills")
    public ApiResponse<List<Map<String, String>>> publicSkills() {
        return ApiResponse.ok(skills.list().stream()
                .map(s -> Map.of("slug", s.slug(), "name", s.name(), "description", s.description()))
                .toList());
    }

    @GetMapping("/session/{sessionId}")
    public ApiResponse<Map<String, Object>> session(@PathVariable String sessionId) {
        AgentMemoryService.Session session = memory.getOrCreate(sessionId);
        return ApiResponse.ok(Map.of(
                "id", session.getId(),
                "title", session.getTitle(),
                "memories", session.getMemories()
        ));
    }

    @GetMapping("/artifacts/list")
    public ApiResponse<List<AgentArtifactService.Artifact>> artifacts() {
        return ApiResponse.ok(artifacts.list());
    }

    @GetMapping("/artifacts")
    public ResponseEntity<Resource> artifact(@RequestParam String path,
                                             @RequestParam(defaultValue = "false") boolean download) {
        try {
            Path file = artifacts.resolve(path);
            if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
            String type = Files.probeContentType(file);
            if (type == null) type = "application/octet-stream";
            boolean inline = !download && (type.startsWith("image/") || type.equals("application/pdf"));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(type));
            headers.setContentLength(Files.size(file));
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    (inline ? "inline" : "attachment") + "; filename*=UTF-8''"
                            + java.net.URLEncoder.encode(file.getFileName().toString(), java.nio.charset.StandardCharsets.UTF_8));
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(file));
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        String question = request.message() == null ? "" : request.message().trim();
        if (question.length() > 4000) question = question.substring(0, 4000);
        return tasks.start(request.sessionId(), request.clientTaskId(), question,
                request.mode(), request.knowledgeBaseIds(), request.benchmarkVariant());
    }

    @GetMapping("/tasks/by-client/{clientTaskId}")
    public ApiResponse<AgentTaskService.TaskView> taskByClient(@PathVariable String clientTaskId) {
        AgentTaskService.TaskView view = tasks.byClientTaskId(clientTaskId);
        return view == null ? ApiResponse.fail(404, "任务不存在") : ApiResponse.ok(view);
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<AgentTaskService.TaskView> task(@PathVariable String taskId) {
        AgentTaskService.TaskView view = tasks.view(taskId);
        return view == null ? ApiResponse.fail(404, "任务不存在") : ApiResponse.ok(view);
    }

    @GetMapping("/tasks/{taskId}/events")
    public ApiResponse<List<AgentRunService.RunEvent>> taskEvents(@PathVariable String taskId,
                                                                 @RequestParam(defaultValue = "0") long after) {
        return ApiResponse.ok(tasks.events(taskId, after));
    }

    @GetMapping(value = "/tasks/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter taskStream(@PathVariable String taskId,
                                 @RequestParam(defaultValue = "0") long after) {
        return tasks.attach(taskId, after);
    }

    @PostMapping(value = "/tasks/{taskId}/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@PathVariable String taskId) {
        return tasks.resume(taskId);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ApiResponse<Boolean> cancel(@PathVariable String taskId) {
        return ApiResponse.ok(tasks.cancel(taskId));
    }
}
