package com.sunnyblog.digitalhuman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.digitalhuman.config.HunyuanMotionProperties;
import com.sunnyblog.digitalhuman.dto.HunyuanMotionJobVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tencent TokenHub HY-3D-Motion client.
 *
 * The API key is provided later through HUNYUAN_MOTION_API_KEY. Until then
 * the feature reports a clear "not configured" state instead of failing at startup.
 */
@Slf4j
@Service
public class HunyuanMotionService {

    private final HunyuanMotionProperties properties;
    private final RestClient client;
    private final Path motionDir;
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    public HunyuanMotionService(HunyuanMotionProperties properties,
                                @Value("${app.digital-human.motion-dir:./data/digital-human/motions}") String motionDir) {
        this.properties = properties;
        this.motionDir = Path.of(motionDir).toAbsolutePath().normalize();
        this.client = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
        try {
            Files.createDirectories(this.motionDir);
        } catch (Exception ex) {
            log.warn("Unable to create Hunyuan motion directory: {}", ex.getMessage());
        }
        log.info("HunyuanMotionService initialized: baseUrl={}, model={}, key={}",
                properties.getBaseUrl(), properties.getModel(), properties.maskedKey());
    }

    public boolean isConfigured() {
        return properties.configured();
    }

    public HunyuanMotionJobVO submit(String prompt, Integer duration) {
        requireConfigured();
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("请先描述想要的动作");
        }
        String normalizedPrompt = prompt.trim();
        if (normalizedPrompt.length() > 128) {
            throw new BusinessException("动作描述不能超过 128 个字符");
        }
        int seconds = duration == null ? 5 : Math.max(1, Math.min(12, duration));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("prompt", normalizedPrompt);
        body.put("duration", seconds);
        body.put("enable_mesh", true);
        body.put("enable_rewrite", true);
        body.put("enable_duration_est", true);

        JsonNode response = post("/v1/api/3d/submit", body);
        String id = firstText(response, "id", "job_id", "jobId");
        if (id == null) {
            String nested = firstNestedText(response, "data", "id", "job_id");
            id = nested != null ? nested : UUID.randomUUID().toString();
        }
        JobState state = new JobState(id, normalizedPrompt);
        state.status = firstText(response, "status");
        if (state.status == null) state.status = "queued";
        jobs.put(id, state);
        log.info("Hunyuan motion submitted: id={}, prompt={}", id, normalizedPrompt);
        return state.toVO(properties.configured());
    }

    public HunyuanMotionJobVO query(String id) {
        requireConfigured();
        JobState cached = jobs.get(id);
        if (cached == null) {
            cached = new JobState(id, "");
            jobs.put(id, cached);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("id", id);
        JsonNode response = post("/v1/api/3d/query", body);

        String status = firstText(response, "status");
        if (status == null) status = firstNestedText(response, "data", "status");
        if (status != null) cached.status = status;
        String error = firstText(response, "error", "error_message", "message");
        if (error != null) cached.error = error;

        String remote = findFileUrl(response);
        if (remote != null && (cached.remoteUrl == null || !remote.equals(cached.remoteUrl))) {
            cached.remoteUrl = remote;
            cached.localFile = download(remote, id);
        }
        if (cached.status == null) cached.status = "in_progress";
        return cached.toVO(properties.configured());
    }

    public HunyuanMotionJobVO status() {
        return new HunyuanMotionJobVO(
                null,
                properties.configured() ? "configured" : "not_configured",
                null,
                null,
                null,
                properties.configured() ? null : "等待配置混元 API Key",
                properties.configured()
        );
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new BusinessException(503, "混元文生动作 API Key 尚未配置");
        }
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            return client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("Hunyuan motion API failed: {}", ex.getMessage());
            throw new BusinessException(502, "混元动作服务调用失败：" + ex.getMessage());
        }
    }

    private String download(String url, String id) {
        try {
            byte[] bytes = client.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(byte[].class);
            if (bytes == null || bytes.length == 0) return null;
            String filename = "hunyuan_" + id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".fbx";
            Path target = motionDir.resolve(filename).normalize();
            if (!target.startsWith(motionDir)) return null;
            Files.write(target, bytes);
            log.info("Hunyuan motion saved: {} ({} bytes)", target, bytes.length);
            return "/api/digital-human/motions/" + filename;
        } catch (Exception ex) {
            log.warn("Unable to download Hunyuan motion: {}", ex.getMessage());
            return null;
        }
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && value.isValueNode()) {
                String text = value.asText();
                if (!text.isBlank()) return text;
            }
        }
        return null;
    }

    private String firstNestedText(JsonNode node, String objectKey, String... keys) {
        if (node == null) return null;
        return firstText(node.path(objectKey), keys);
    }

    private String findFileUrl(JsonNode node) {
        if (node == null) return null;
        String direct = firstText(node, "url", "file_url", "fileUrl", "download_url", "downloadUrl");
        if (direct != null) return direct;
        JsonNode data = node.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                String nested = firstText(item, "url", "file_url", "fileUrl", "download_url", "downloadUrl");
                if (nested != null) return nested;
            }
        }
        if (data != null && data.isObject()) {
            return findFileUrl(data);
        }
        return null;
    }

    private static final class JobState {
        private final String id;
        private final String prompt;
        private String status = "queued";
        private String remoteUrl;
        private String localFile;
        private String error;

        private JobState(String id, String prompt) {
            this.id = id;
            this.prompt = prompt;
        }

        private HunyuanMotionJobVO toVO(boolean configured) {
            return new HunyuanMotionJobVO(
                    id,
                    status,
                    prompt,
                    localFile,
                    remoteUrl,
                    error,
                    configured
            );
        }
    }
}
