package com.sunnyblog.digitalhuman.controller;

import com.sunnyblog.common.ApiResponse;
import com.sunnyblog.digitalhuman.dto.DhCharacterVO;
import com.sunnyblog.digitalhuman.dto.DhChatRequest;
import com.sunnyblog.digitalhuman.dto.DhCreateSessionRequest;
import com.sunnyblog.digitalhuman.dto.DhSessionVO;
import com.sunnyblog.digitalhuman.dto.DhTtsRequest;
import com.sunnyblog.digitalhuman.dto.DhTtsResponse;
import com.sunnyblog.digitalhuman.dto.HunyuanMotionJobVO;
import com.sunnyblog.digitalhuman.dto.HunyuanMotionRequest;
import com.sunnyblog.digitalhuman.model.DhSession;
import com.sunnyblog.digitalhuman.service.DigitalHumanCharacterService;
import com.sunnyblog.digitalhuman.service.DigitalHumanChatService;
import com.sunnyblog.digitalhuman.service.DigitalHumanSessionService;
import com.sunnyblog.digitalhuman.service.DigitalHumanStreamService;
import com.sunnyblog.digitalhuman.service.HunyuanMotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Tag(name = "前台 · 3D 数字人")
@RestController
@RequestMapping("/api/digital-human")
@RequiredArgsConstructor
public class DigitalHumanController {

    private final DigitalHumanCharacterService characterService;
    private final DigitalHumanSessionService sessionService;
    private final DigitalHumanChatService chatService;
    private final DigitalHumanStreamService streamService;
    private final HunyuanMotionService hunyuanMotionService;

    @Operation(summary = "数字人角色列表")
    @GetMapping("/characters")
    public ApiResponse<List<DhCharacterVO>> characters() {
        return ApiResponse.ok(characterService.listActive());
    }

    @Operation(summary = "创建数字人会话")
    @PostMapping("/sessions")
    public ApiResponse<DhSessionVO> createSession(@Valid @RequestBody DhCreateSessionRequest request) {
        DhSession session = sessionService.create(request.characterCode());
        return ApiResponse.ok(sessionService.getSessionVO(session.getId()));
    }

    @Operation(summary = "读取数字人会话与历史")
    @GetMapping("/sessions/{id}")
    public ApiResponse<DhSessionVO> getSession(@PathVariable Long id) {
        return ApiResponse.ok(sessionService.getSessionVO(id));
    }

    @Operation(summary = "结束数字人会话")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        sessionService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "和数字人对话（纯文字，不走 TTS/动作，用于长会话测试）")
    @PostMapping("/chat/plain")
    public ApiResponse<DigitalHumanChatService.PlainReply> chatPlain(@Valid @RequestBody DhChatRequest request) {
        return ApiResponse.ok(chatService.plainReply(request));
    }

    @Operation(summary = "和数字人对话（SSE 流式文字 + 语音）")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody DhChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        streamService.stream(request, emitter);
        return emitter;
    }

    @Operation(summary = "和数字人对话（SSE 流式文字 + 动作先导）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody DhChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);
        streamService.stream(request, emitter);
        return emitter;
    }

    @Operation(summary = "为流式回复的一句话合成语音")
    @PostMapping("/tts")
    public ApiResponse<DhTtsResponse> tts(@Valid @RequestBody DhTtsRequest request) {
        return ApiResponse.ok(chatService.synthesizeSentence(request.characterCode(), request.text()));
    }

    @Operation(summary = "数字人服务状态")
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "characters", characterService.listActive().size(),
                "engine", "Spring AI + DeepSeek Flash",
                "voice", "DashScope CosyVoice",
                "renderer", "Three.js / WebGL",
                "hunyuanMotion", hunyuanMotionService.isConfigured()
        ));
    }

    @Operation(summary = "混元文生动作配置状态")
    @GetMapping("/motions/hunyuan/status")
    public ApiResponse<HunyuanMotionJobVO> hunyuanStatus() {
        return ApiResponse.ok(hunyuanMotionService.status());
    }

    @Operation(summary = "提交混元文生动作任务")
    @PostMapping("/motions/hunyuan")
    public ApiResponse<HunyuanMotionJobVO> submitHunyuanMotion(@Valid @RequestBody HunyuanMotionRequest request) {
        return ApiResponse.ok(hunyuanMotionService.submit(request.prompt(), request.duration()));
    }

    @Operation(summary = "查询混元文生动作任务")
    @GetMapping("/motions/hunyuan/{id}")
    public ApiResponse<HunyuanMotionJobVO> queryHunyuanMotion(@PathVariable String id) {
        return ApiResponse.ok(hunyuanMotionService.query(id));
    }
}
