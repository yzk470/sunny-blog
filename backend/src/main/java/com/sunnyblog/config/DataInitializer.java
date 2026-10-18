package com.sunnyblog.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sunnyblog.entity.BlogUser;
import com.sunnyblog.mapper.BlogUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 首次启动时创建后台管理员账号（默认 admin / admin123，可通过环境变量覆盖）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final BlogUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.nickname}")
    private String adminNickname;

    @Override
    public void run(ApplicationArguments args) {
        long count = userMapper.selectCount(Wrappers.lambdaQuery(BlogUser.class)
                .eq(BlogUser::getUsername, adminUsername));
        if (count > 0) {
            log.info("Admin account '{}' already exists", adminUsername);
            return;
        }
        BlogUser user = new BlogUser();
        user.setUsername(adminUsername);
        user.setPasswordHash(passwordEncoder.encode(adminPassword));
        user.setNickname(adminNickname);
        user.setAvatar("/images/avatar-yuebai.png");
        user.setRole("ADMIN");
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("Created default admin account '{}'. Please change the password in production.", adminUsername);
    }
}
