package com.sunnyblog.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.dto.AuthUserVO;
import com.sunnyblog.dto.LoginResponse;
import com.sunnyblog.dto.request.LoginRequest;
import com.sunnyblog.entity.BlogUser;
import com.sunnyblog.mapper.BlogUserMapper;
import com.sunnyblog.security.JwtService;
import com.sunnyblog.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final BlogUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    public LoginResponse login(LoginRequest request) {
        BlogUser user = userMapper.selectOne(Wrappers.lambdaQuery(BlogUser.class)
                .eq(BlogUser::getUsername, request.username().trim()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码不正确");
        }
        String token = jwtService.generate(user);
        return new LoginResponse(token, jwtService.getExpireSeconds(), toVO(user));
    }

    @Override
    public AuthUserVO me(Long userId) {
        BlogUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "账号不存在");
        }
        return toVO(user);
    }

    private AuthUserVO toVO(BlogUser user) {
        return new AuthUserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar(), user.getRole());
    }
}
