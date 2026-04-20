package com.bjtu.dining.user.vo;

import com.bjtu.dining.user.entity.User;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class UserProfileResponse {
    private final Long userId;
    private final String username;
    private final String nickname;
    private final String avatarUrl;
    private final String registerTime;

    public UserProfileResponse(Long userId, String username, String nickname, String avatarUrl, String registerTime) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.registerTime = registerTime;
    }

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getUserId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getCreateTime().atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
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

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getRegisterTime() {
        return registerTime;
    }
}
