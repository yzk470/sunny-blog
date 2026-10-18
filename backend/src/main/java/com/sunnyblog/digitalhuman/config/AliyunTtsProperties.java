package com.sunnyblog.digitalhuman.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.tts")
public class AliyunTtsProperties {

    private String apiKey;
    private String apiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    private String model = "cosyvoice-v3-flash";
    private String voice = "longyingxiao_v3";
    private int sampleRate = 24000;
    private String responseFormat = "wav";

    public String maskedKey() {
        if (apiKey == null || apiKey.isBlank()) {
            return "not-configured";
        }
        return apiKey.length() <= 8 ? "****" : apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
