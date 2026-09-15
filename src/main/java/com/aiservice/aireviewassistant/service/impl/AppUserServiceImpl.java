package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.AppUser;
import com.aiservice.aireviewassistant.exception.BusinessException;
import com.aiservice.aireviewassistant.mapper.AppUserMapper;
import com.aiservice.aireviewassistant.service.AppUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务实现类。
 * <p>基于 MyBatis-Plus 通用 Service 实现用户注册、查询逻辑。
 * 密码采用 Spring Security 提供的 PasswordEncoder 单向加密，不保存明文。</p>
 */
@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements AppUserService {

    private final PasswordEncoder passwordEncoder;

    /**
     * 构造方法，注入密码编码器。
     *
     * @param passwordEncoder Spring Security 密码加密器，用于注册时加密密码
     */
    public AppUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AppUser register(String username, String password, String nickname) {
        // 权限校验：检查用户名是否已存在，存在则抛出业务异常阻止重复注册
        AppUser existing = findByUsername(username);
        if (existing != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        // 密码处理：使用 PasswordEncoder 对原始密码进行单向加密，避免明文落库
        user.setPassword(passwordEncoder.encode(password));
        // 复杂条件分支：昵称未提供或为空时，默认回退到用户名作为展示名称
        user.setNickname(nickname != null && !nickname.isEmpty() ? nickname : username);
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        // 调用 MyBatis-Plus 通用保存方法，将新用户持久化到数据库
        save(user);
        return user;
    }

    @Override
    public AppUser findByUsername(String username) {
        // 使用 Lambda 查询条件精确匹配用户名，只返回一条记录
        return lambdaQuery().eq(AppUser::getUsername, username).one();
    }
}
