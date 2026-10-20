package com.sunnyblog.agent;

import com.sunnyblog.common.ApiResponse;
import jakarta.annotation.PreDestroy;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentMemoryService memory;
    private final AgentSkillsService skills;
    private final AgentLlmService llm;
    private final AgentProperties properties;
    private final AgentArtifactService artifacts;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public record ChatRequest(String sessionId, String message, String mode, List<String> knowledgeBaseIds) {
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
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
        SseEmitter emitter = new SseEmitter(600_000L);
        String question = request.message() == null ? "" : request.message().trim();
        if (question.length() > 4000) question = question.substring(0, 4000);
        final String safeQuestion = question;
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> log.debug("Agent SSE disconnected: {}", ex.getMessage()));
        executor.submit(() -> {
            try {
                orchestrator.chat(request.sessionId(), safeQuestion, request.mode(),
                        request.knowledgeBaseIds(), event -> send(emitter, event));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("Agent chat failed: {}", ex.getMessage(), ex);
                send(emitter, new AgentEvent("error", "执行失败", ex.getMessage()));
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, AgentEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", event.message());
            payload.put("detail", clip(event.detail()));
            emitter.send(SseEmitter.event().name(event.type()).data(payload));
        } catch (Exception ex) {
            log.debug("Agent SSE send failed: {}", ex.getMessage());
        }
    }

    private String clip(String detail) {
        if (detail == null) return "";
        int max = properties.getMaxOutputChars();
        return detail.length() <= max ? detail : detail.substring(0, max) + "\n...[已截断]";
    }
}
