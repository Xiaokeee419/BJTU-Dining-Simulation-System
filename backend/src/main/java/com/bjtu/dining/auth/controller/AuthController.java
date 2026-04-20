package com.bjtu.dining.auth.controller;

import com.bjtu.dining.auth.dto.LoginRequest;
import com.bjtu.dining.auth.dto.RegisterRequest;
import com.bjtu.dining.auth.service.AuthService;
import com.bjtu.dining.auth.vo.LoginResponse;
import com.bjtu.dining.auth.vo.RegisterResponse;
import com.bjtu.dining.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
