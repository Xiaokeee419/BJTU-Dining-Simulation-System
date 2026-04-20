package com.bjtu.dining.auth.vo;

import com.bjtu.dining.user.entity.User;

public class LoginUserResponse {
    private final Long userId;
    private final String username;
    private final String nickname;

    public LoginUserResponse(Long userId, String username, String nickname) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
    }

    public static LoginUserResponse from(User user) {
        return new LoginUserResponse(user.getUserId(), user.getUsername(), user.getNickname());
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }
}
