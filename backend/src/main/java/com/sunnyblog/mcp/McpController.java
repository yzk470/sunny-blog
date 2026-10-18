package com.sunnyblog.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunnyblog.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Minimal MCP Streamable HTTP endpoint.
 *
 * <p>MCP clients POST JSON-RPC requests to {@code /api/mcp}. The server is
 * stateless and supports the tools capability only. Authentication uses a
 * dedicated bearer token so an AI client never needs the admin password.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String NAME = "yuebai-blog";

    private final McpArticleService articleService;
    private final ObjectMapper objectMapper;

    @Value("${app.mcp.enabled:true}")
    private boolean enabled;

    @Value("${app.mcp.api-key:}")
    private String apiKey;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> handle(@RequestBody JsonNode payload,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(errorBody(null, -32000, "MCP 服务未配置，请设置 MCP_API_KEY"));
        }
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .body(errorBody(null, -32001, "MCP 认证失败"));
        }

        response.setHeader("Mcp-Session-Id", UUID.randomUUID().toString());
        response.setHeader("Mcp-Protocol-Version", PROTOCOL_VERSION);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        if (payload.isArray()) {
            List<Object> responses = new ArrayList<>();
            payload.forEach(item -> {
                Object result = handleMessage(item);
                if (result != null) {
                    responses.add(result);
                }
            });
            return responses.isEmpty()
                    ? ResponseEntity.accepted().build()
                    : ResponseEntity.ok(responses);
        }

        Object result = handleMessage(payload);
        return result == null ? ResponseEntity.accepted().build() : ResponseEntity.ok(result);
    }

    /**
     * Streamable HTTP clients may probe GET before opening their POST session.
     * This stateless tool server has no server-initiated messages, so 405 is the
     * protocol-compliant answer.
     */
    @GetMapping
    public ResponseEntity<Object> get() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, "POST")
                .build();
    }

    private Object handleMessage(JsonNode message) {
        if (message == null || !message.isObject()) {
            return errorBody(null, -32600, "Invalid JSON-RPC request");
        }
        JsonNode id = message.get("id");
        String method = message.path("method").asText("");
        if (method.isBlank()) {
            return null;
        }
        if (id == null) {
            // MCP notifications (notifications/initialized, notifications/cancelled)
            return null;
        }
        try {
            return switch (method) {
                case "initialize" -> success(id, initializeResult(message));
                case "ping" -> success(id, objectMapper.createObjectNode());
                case "tools/list" -> success(id, toolsResult());
                case "tools/call" -> toolsCall(id, message.path("params"));
                default -> errorBody(id, -32601, "Method not found: " + method);
            };
        } catch (BusinessException e) {
            return errorBody(id, -32602, e.getMessage());
        } catch (Exception e) {
            log.warn("MCP request failed: method={}, error={}", method, e.getMessage());
            return errorBody(id, -32603, "Internal error");
        }
    }

    private ObjectNode initializeResult(JsonNode message) {
        String requested = message.path("params").path("protocolVersion").asText(PROTOCOL_VERSION);
        String version = List.of("2024-11-05", "2025-03-26", "2025-06-18").contains(requested)
                ? requested : PROTOCOL_VERSION;
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", version);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", NAME);
        serverInfo.put("version", "1.0.0");
        result.put("instructions",
                "月白小筑博客文章管理 MCP。可以用 list_articles 查找文章，get_article 读取正文，"
                        + "create_article 创建文章，update_article 修改文章，publish_article 发布/撤回，"
                        + "delete_article 删除文章。正文使用 Markdown。修改前先读取文章，发布前确认内容。");
        return result;
    }

    private ObjectNode toolsResult() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = McpToolCatalog.tools(objectMapper);
        result.set("tools", tools);
        return result;
    }

    private ObjectNode toolsCall(JsonNode id, JsonNode params) {
        String name = params.path("name").asText("");
        if (name.isBlank()) {
            return errorBody(id, -32602, "Missing tool name");
        }
        JsonNode arguments = params.path("arguments");
        try {
            McpArticleService.ToolResult result = articleService.call(name, arguments);
            log.info("MCP tool call: tool={}, error={}", name, result.error());
            ObjectNode response = success(id, toolContent(result));
            return response;
        } catch (BusinessException e) {
            log.info("MCP tool rejected: tool={}, message={}", name, e.getMessage());
            return success(id, toolContent(McpArticleService.ToolResult.error(e.getMessage())));
        } catch (Exception e) {
            log.warn("MCP tool failed: tool={}, error={}", name, e.getMessage());
            return success(id, toolContent(McpArticleService.ToolResult.error("工具执行失败：" + e.getMessage())));
        }
    }

    private ObjectNode toolContent(McpArticleService.ToolResult result) {
        ObjectNode value = objectMapper.createObjectNode();
        ArrayNode content = value.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", result.text());
        value.put("isError", result.error());
        return value;
    }

    private ObjectNode success(JsonNode id, ObjectNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode errorBody(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    private boolean authorized(HttpServletRequest request) {
        String provided = request.getHeader("X-MCP-Key");
        if (provided == null || provided.isBlank()) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                provided = authorization.substring(7).trim();
            }
        }
        if (provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
