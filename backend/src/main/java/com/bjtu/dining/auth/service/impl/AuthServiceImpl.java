package com.bjtu.dining.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bjtu.dining.auth.dto.LoginRequest;
import com.bjtu.dining.auth.security.JwtTokenService;
import com.bjtu.dining.auth.dto.RegisterRequest;
import com.bjtu.dining.auth.service.AuthService;
import com.bjtu.dining.auth.vo.LoginResponse;
import com.bjtu.dining.auth.vo.LoginUserResponse;
import com.bjtu.dining.auth.vo.RegisterResponse;
import com.bjtu.dining.common.enums.ErrorCode;
import com.bjtu.dining.common.exception.BusinessException;
import com.bjtu.dining.user.entity.User;
import com.bjtu.dining.user.mapper.UserMapper;
import com.bjtu.dining.wallet.entity.Wallet;
import com.bjtu.dining.wallet.mapper.WalletMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final WalletMapper walletMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthServiceImpl(UserMapper userMapper,
                           WalletMapper walletMapper,
                           PasswordEncoder passwordEncoder,
                           JwtTokenService jwtTokenService) {
        this.userMapper = userMapper;
        this.walletMapper = walletMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String nickname = request.getNickname().trim();

        if (userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)
        ) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(nickname);
        user.setAvatarUrl("");
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);

        Wallet wallet = new Wallet();
        wallet.setUserId(user.getUserId());
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setUpdateTime(now);
        walletMapper.insert(wallet);

        return new RegisterResponse(user.getUserId(), user.getUsername(), user.getNickname());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)
        );
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtTokenService.createToken(user);
        return new LoginResponse(
                token,
                jwtTokenService.getExpireSeconds(),
                LoginUserResponse.from(user)
        );
    }
}
