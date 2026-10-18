package com.sunnyblog.service;

import com.sunnyblog.dto.AuthUserVO;
import com.sunnyblog.dto.LoginResponse;
import com.sunnyblog.dto.request.LoginRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    AuthUserVO me(Long userId);
}
