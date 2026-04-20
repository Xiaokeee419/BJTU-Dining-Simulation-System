package com.bjtu.dining.auth.service;

import com.bjtu.dining.auth.dto.LoginRequest;
import com.bjtu.dining.auth.dto.RegisterRequest;
import com.bjtu.dining.auth.vo.LoginResponse;
import com.bjtu.dining.auth.vo.RegisterResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);
}
