package com.sunnyblog.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagEmbeddingService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build();

    public boolean isConfigured() {
        return properties.getEmbeddingKey() != null && !properties.getEmbeddingKey().isBlank();
    }

    public List<Double> embedText(String text) {
        if (!isConfigured()) throw new IllegalStateException("RAG 向量模型未配置 DASHSCOPE_API_KEY");
        if (text == null || text.isBlank()) throw new IllegalArgumentException("不能向量化空文本");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getEmbeddingModel());
        ObjectNode input = body.putObject("input");
        ArrayNode contents = input.putArray("contents");
        contents.addObject().put("text", text);
        ObjectNode parameters = body.putObject("parameters");
        parameters.put("dimension", properties.getDimension());
        parameters.put("output_type", "dense");
        return call(body);
    }

    public List<Double> embedImage(Path path, String mimeType) {
        if (!isConfigured()) throw new IllegalStateException("RAG 向量模型未配置 DASHSCOPE_API_KEY");
        try {
            if (Files.size(path) > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("图片不能超过 10MB");
            }
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(path));
            String type = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType;
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", properties.getEmbeddingModel());
            ObjectNode input = body.putObject("input");
            ArrayNode contents = input.putArray("contents");
            contents.addObject().put("image", "data:" + type + ";base64," + base64);
            ObjectNode parameters = body.putObject("parameters");
            parameters.put("dimension", properties.getDimension());
            parameters.put("output_type", "dense");
            return call(body);
        } catch (IOException ex) {
            throw new IllegalStateException("读取图片失败: " + ex.getMessage(), ex);
        }
    }

    private List<Double> call(ObjectNode body) {
        String base = properties.getEmbeddingBaseUrl().replaceAll("/+$", "");
        String url = base + "/services/embeddings/multimodal-embedding/multimodal-embedding";
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + properties.getEmbeddingKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                String text = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("向量接口 HTTP " + response.code() + ": " + clip(text));
                }
                JsonNode root = objectMapper.readTree(text);
                JsonNode embeddings = root.path("output").path("embeddings");
                JsonNode embedding = null;
                if (embeddings.isArray() && !embeddings.isEmpty()) {
                    embedding = embeddings.get(0).path("embedding");
                } else {
                    embedding = root.path("output").path("embedding");
                }
                if (!embedding.isArray() || embedding.isEmpty()) {
                    String code = root.path("code").asText("");
                    String message = root.path("message").asText("");
                    throw new IllegalStateException("向量接口未返回 embedding: " + code + " " + message + " " + clip(text));
                }
                List<Double> vector = new ArrayList<>(embedding.size());
                for (JsonNode value : embedding) vector.add(value.asDouble());
                if (vector.size() != properties.getDimension()) {
                    log.warn("Embedding dimension {} differs from configured {}", vector.size(), properties.getDimension());
                }
                return vector;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("向量接口调用失败: " + ex.getMessage(), ex);
        }
    }

    private String clip(String text) {
        if (text == null) return "";
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }
}
