package com.sunnyblog.digitalhuman.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hunyuan.motion")
public class HunyuanMotionProperties {

    private String apiKey;
    private String baseUrl = "https://tokenhub.tencentmaas.com";
    private String model = "hy-3d-motion";

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String maskedKey() {
        if (!configured()) return "not-configured";
        return apiKey.length() <= 8 ? "****" : apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
