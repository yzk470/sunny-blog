package com.sunnyblog.agent;

import com.sunnyblog.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/agent")
@RequiredArgsConstructor
public class AgentAdminController {

    private final AgentSkillsService skills;
    private final AgentRunService runs;
    private final AgentArtifactService artifacts;
    private final AgentMetricsService metrics;
    private final AgentBenchmarkService benchmark;

    public record SkillRequest(String slug, String name, String description, String content) {
    }

    public record BenchmarkRequest(String variants, int repeats, List<String> taskIds) {
    }

    @GetMapping("/skills")
    public ApiResponse<List<AgentSkillsService.Skill>> skills() {
        return ApiResponse.ok(skills.list());
    }

    @PostMapping("/skills")
    public ApiResponse<AgentSkillsService.Skill> save(@RequestBody SkillRequest request) {
        return ApiResponse.ok(skills.save(request.slug(), request.name(),
                request.description(), request.content()));
    }

    @PostMapping("/skills/zip")
    public ApiResponse<AgentSkillsService.Skill> uploadZip(@RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename() == null ? "skill" : file.getOriginalFilename();
            String fallback = filename.replaceAll("\\.zip$", "");
            return ApiResponse.ok(skills.importZip(file.getBytes(), fallback));
        } catch (Exception ex) {
            return ApiResponse.fail(500, "导入技能目录失败: " + ex.getMessage());
        }
    }

    @PostMapping("/skills/upload")
    public ApiResponse<AgentSkillsService.Skill> upload(@RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "name", required = false) String name,
                                                         @RequestParam(value = "description", required = false) String description) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String filename = file.getOriginalFilename() == null ? "imported-skill" : file.getOriginalFilename();
            String finalName = (name == null || name.isBlank()) ? filename.replaceAll("\\.md$", "") : name;
            return ApiResponse.ok(skills.save(null, finalName, description, content));
        } catch (Exception ex) {
            return ApiResponse.fail(500, "导入 skill 失败: " + ex.getMessage());
        }
    }

    @DeleteMapping("/skills/{slug}")
    public ApiResponse<Void> delete(@PathVariable String slug) {
        skills.delete(slug);
        return ApiResponse.ok();
    }

    @GetMapping("/runs")
    public ApiResponse<List<AgentRunService.AgentRun>> runs() {
        return ApiResponse.ok(runs.list());
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<AgentRunService.AgentRun> run(@PathVariable String id) {
        AgentRunService.AgentRun run = runs.get(id);
        return run == null ? ApiResponse.fail(404, "运行记录不存在") : ApiResponse.ok(run);
    }

    @GetMapping("/metrics")
    public ApiResponse<AgentMetricsService.Snapshot> metrics() {
        return ApiResponse.ok(metrics.snapshot());
    }

    @GetMapping("/metrics/summary")
    public ApiResponse<AgentMetricsService.Snapshot> metricsSummary(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(metrics.summary(days));
    }

    @GetMapping("/metrics/trend")
    public ApiResponse<List<AgentMetricsRepository.DailyRow>> metricsTrend(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(metrics.trend(days));
    }

    @GetMapping("/metrics/failures")
    public ApiResponse<List<java.util.Map<String, Object>>> metricsFailures(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(metrics.failures(days));
    }

    @GetMapping("/metrics/tasks")
    public ApiResponse<List<AgentMetricsRepository.TaskRow>> metricsTasks(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "") String failureType) {
        return ApiResponse.ok(metrics.recentTasks(limit, failureType));
    }

    @PostMapping("/benchmark/start")
    public ApiResponse<AgentBenchmarkService.Status> startBenchmark(@RequestBody BenchmarkRequest request) {
        try {
            return ApiResponse.ok(benchmark.start(request.variants(),
                    request.repeats() <= 0 ? 3 : request.repeats(), request.taskIds()));
        } catch (Exception ex) {
            return ApiResponse.fail(500, ex.getMessage());
        }
    }

    @GetMapping("/benchmark/status")
    public ApiResponse<AgentBenchmarkService.Status> benchmarkStatus() {
        return ApiResponse.ok(benchmark.status());
    }

    @PostMapping("/benchmark/stop")
    public ApiResponse<Boolean> stopBenchmark() {
        benchmark.stop();
        return ApiResponse.ok(true);
    }

    @GetMapping("/artifacts")
    public ApiResponse<List<AgentArtifactService.Artifact>> artifacts() {
        return ApiResponse.ok(artifacts.list());
    }

    @DeleteMapping("/artifacts")
    public ApiResponse<Void> deleteArtifact(@RequestParam String path) {
        try {
            return artifacts.delete(path) ? ApiResponse.ok() : ApiResponse.fail(404, "产物不存在");
        } catch (Exception ex) {
            return ApiResponse.fail(500, ex.getMessage());
        }
    }
}
