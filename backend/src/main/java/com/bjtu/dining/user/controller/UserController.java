package com.bjtu.dining.user.controller;

import com.bjtu.dining.auth.security.AuthUserContext;
import com.bjtu.dining.common.response.ApiResponse;
import com.bjtu.dining.user.service.UserService;
import com.bjtu.dining.user.vo.UserProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUserProfile() {
        return ApiResponse.success(userService.getCurrentUserProfile(AuthUserContext.currentUserId()));
    }
}
