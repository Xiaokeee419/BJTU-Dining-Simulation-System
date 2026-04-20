package com.bjtu.dining.user.service.impl;

import com.bjtu.dining.common.enums.ErrorCode;
import com.bjtu.dining.common.exception.BusinessException;
import com.bjtu.dining.user.entity.User;
import com.bjtu.dining.user.mapper.UserMapper;
import com.bjtu.dining.user.service.UserService;
import com.bjtu.dining.user.vo.UserProfileResponse;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserProfileResponse getCurrentUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return UserProfileResponse.from(user);
    }
}
