package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.AppUser;
import com.aiservice.aireviewassistant.exception.BusinessException;
import com.aiservice.aireviewassistant.mapper.AppUserMapper;
import com.aiservice.aireviewassistant.service.AppUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 用户表 服务实现类
@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements AppUserService {

    private final PasswordEncoder passwordEncoder;

    public AppUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AppUser register(String username, String password, String nickname) {
        // 检查用户名是否已存在
        AppUser existing = findByUsername(username);
        if (existing != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null && !nickname.isEmpty() ? nickname : username);
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        save(user);
        return user;
    }

    @Override
    public AppUser findByUsername(String username) {
        return lambdaQuery().eq(AppUser::getUsername, username).one();
    }
}
