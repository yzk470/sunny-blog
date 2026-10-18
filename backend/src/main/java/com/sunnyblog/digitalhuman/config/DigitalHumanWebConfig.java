package com.sunnyblog.digitalhuman.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
public class DigitalHumanWebConfig implements WebMvcConfigurer {

    @Value("${app.digital-human.audio-dir:./data/digital-human/audio}")
    private String audioDir;

    @Value("${app.digital-human.motion-dir:./data/digital-human/motions}")
    private String motionDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        addDirectoryHandler(registry, "/api/digital-human/audio/**", audioDir);
        addDirectoryHandler(registry, "/api/digital-human/motions/**", motionDir);
    }

    private void addDirectoryHandler(ResourceHandlerRegistry registry, String pattern, String directory) {
        try {
            Path path = Path.of(directory).toAbsolutePath().normalize();
            Files.createDirectories(path);
            String location = path.toUri().toString();
            if (!location.endsWith("/")) {
                location += "/";
            }
            registry.addResourceHandler(pattern)
                    .addResourceLocations(location)
                    .setCachePeriod(3600);
            log.info("Digital human assets: {} -> {}", pattern, path);
        } catch (Exception ex) {
            log.warn("Unable to prepare digital human directory {}: {}", directory, ex.getMessage());
        }
    }
}
