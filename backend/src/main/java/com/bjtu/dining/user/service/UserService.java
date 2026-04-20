package com.bjtu.dining.user.service;

import com.bjtu.dining.user.vo.UserProfileResponse;

public interface UserService {
    UserProfileResponse getCurrentUserProfile(Long userId);
}
