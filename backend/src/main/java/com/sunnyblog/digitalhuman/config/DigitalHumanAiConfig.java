package com.sunnyblog.digitalhuman.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * DeepSeek / 远端模型的流式响应可能较慢，放宽 Spring AI RestClient 的读取超时。
 */
@Slf4j
@Configuration
public class DigitalHumanAiConfig {

    @Bean
    public RestClientCustomizer digitalHumanRestClientCustomizer() {
        return builder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(180));
            builder.requestFactory(factory);
            log.info("Digital human AI RestClient timeout configured: read=180s");
        };
    }
}
