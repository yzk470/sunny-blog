package com.sunnyblog.rag;

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

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagService ragService;

    public record CreateRequest(String name, String description) {
    }

    public record SearchRequest(String query, Integer topK) {
    }

    @GetMapping("/databases")
    public ApiResponse<List<RagService.RagDatabase>> databases() {
        return ApiResponse.ok(ragService.listDatabases());
    }

    @PostMapping("/databases")
    public ApiResponse<RagService.RagDatabase> create(@RequestBody CreateRequest request) {
        return ApiResponse.ok(ragService.createDatabase(request.name(), request.description()));
    }

    @DeleteMapping("/databases/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        ragService.deleteDatabase(id);
        return ApiResponse.ok();
    }

    @GetMapping("/databases/{id}/files")
    public ApiResponse<List<RagService.RagFile>> files(@PathVariable String id) {
        RagService.RagDatabase db = ragService.getDatabase(id);
        return db == null ? ApiResponse.fail(404, "知识库不存在") : ApiResponse.ok(db.getFiles());
    }

    @PostMapping("/databases/{id}/files")
    public ApiResponse<RagService.RagFile> upload(@PathVariable String id,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "FIXED") String strategy,
                                                   @RequestParam(defaultValue = "800") int chunkSize,
                                                   @RequestParam(defaultValue = "120") int overlap) {
        try {
            RagChunkingService.Strategy mode = RagChunkingService.Strategy.valueOf(strategy.toUpperCase(Locale.ROOT));
            return ApiResponse.ok(ragService.addFile(id, file, mode, chunkSize, overlap));
        } catch (Exception ex) {
            return ApiResponse.fail(500, ex.getMessage());
        }
    }

    @PostMapping("/preview")
    public ApiResponse<RagService.Preview> preview(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(defaultValue = "FIXED") String strategy,
                                                    @RequestParam(defaultValue = "800") int chunkSize,
                                                    @RequestParam(defaultValue = "120") int overlap) {
        try {
            RagChunkingService.Strategy mode = RagChunkingService.Strategy.valueOf(strategy.toUpperCase(Locale.ROOT));
            return ApiResponse.ok(ragService.preview(file, mode, chunkSize, overlap));
        } catch (Exception ex) {
            return ApiResponse.fail(500, ex.getMessage());
        }
    }

    @DeleteMapping("/databases/{id}/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable String id, @PathVariable String fileId) {
        ragService.deleteFile(id, fileId);
        return ApiResponse.ok();
    }

    @PostMapping("/databases/{id}/search")
    public ApiResponse<List<RagService.Retrieved>> search(@PathVariable String id,
                                                          @RequestBody SearchRequest request) {
        try {
            int topK = request.topK() == null ? 5 : request.topK();
            return ApiResponse.ok(ragService.search(request.query(), List.of(id), topK));
        } catch (Exception ex) {
            return ApiResponse.fail(500, ex.getMessage());
        }
    }
}
