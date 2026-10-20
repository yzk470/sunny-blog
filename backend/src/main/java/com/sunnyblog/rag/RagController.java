package com.sunnyblog.rag;

import com.sunnyblog.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @GetMapping("/databases")
    public ApiResponse<List<Map<String, Object>>> databases() {
        return ApiResponse.ok(ragService.listDatabases().stream()
                .map(db -> Map.<String, Object>of(
                        "id", db.getId(),
                        "name", db.getName(),
                        "description", db.getDescription(),
                        "files", db.getFiles().size()
                ))
                .toList());
    }
}
