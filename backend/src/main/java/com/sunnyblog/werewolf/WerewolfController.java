package com.sunnyblog.werewolf;

import com.sunnyblog.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/werewolf")
@RequiredArgsConstructor
public class WerewolfController {

    private final WerewolfService service;

    public record CreateRequest(
            @NotBlank(message = "请输入你的昵称")
            @Size(max = 12, message = "昵称最多 12 个字")
            String playerName,
            @Min(value = 6, message = "至少 6 名玩家")
            @Max(value = 12, message = "最多 12 名玩家")
            Integer playerCount,
            String role
    ) {}

    public record ActionRequest(String type, String targetId, String text) {}

    @PostMapping("/games")
    public ApiResponse<WerewolfGame.GameView> create(@Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(service.create(request.playerName(), request.playerCount(), request.role()));
    }

    @GetMapping("/games/{id}")
    public ApiResponse<WerewolfGame.GameView> get(@PathVariable String id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/games/{id}/advance")
    public ApiResponse<WerewolfGame.GameView> advance(@PathVariable String id) {
        return ApiResponse.ok(service.advance(id));
    }

    @PostMapping("/games/{id}/action")
    public ApiResponse<WerewolfGame.GameView> action(@PathVariable String id,
                                                     @RequestBody ActionRequest request) {
        return ApiResponse.ok(service.action(id, request.type(), request.targetId(), request.text()));
    }

    @PostMapping("/games/{id}/auto")
    public ApiResponse<WerewolfGame.GameView> auto(@PathVariable String id) {
        return ApiResponse.ok(service.auto(id));
    }

    @DeleteMapping("/games/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
