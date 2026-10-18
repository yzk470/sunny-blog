package com.sunnyblog;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan({"com.sunnyblog.mapper", "com.sunnyblog.digitalhuman.mapper"})
@SpringBootApplication
public class SunnyBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(SunnyBlogApplication.class, args);
    }
}
