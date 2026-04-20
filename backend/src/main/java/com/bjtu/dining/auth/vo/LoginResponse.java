package com.bjtu.dining.auth.vo;

public class LoginResponse {
    private final String token;
    private final long expiresIn;
    private final LoginUserResponse user;

    public LoginResponse(String token, long expiresIn, LoginUserResponse user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public LoginUserResponse getUser() {
        return user;
    }
}
