package com.sunnyblog.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    private String redisHost = "127.0.0.1";
    private int redisPort = 6380;
    private String dir = "./data/agent/rag";
    private String embeddingKey = "";
    private String embeddingBaseUrl = "https://maas.qianwenaiapi.com/api/v1";
    private String embeddingModel = "qwen3-vl-embedding";
    private int dimension = 1024;
    private int coarseTopK = 12;
    private int finalTopK = 5;
    private int maxChars = 800;
    private int overlap = 120;
}
