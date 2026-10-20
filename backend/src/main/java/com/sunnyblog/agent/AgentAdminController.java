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

    public record SkillRequest(String slug, String name, String description, String content) {
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
