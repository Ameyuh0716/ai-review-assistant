package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.AppUser;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户服务接口。
 * <p>负责用户注册、用户名唯一性校验及按用户名查询等认证相关操作。
 * 密码加密由实现类通过 Spring Security 的 {@link org.springframework.security.crypto.password.PasswordEncoder}
 * 完成，本接口不暴露原始密码处理逻辑。</p>
 */
public interface AppUserService extends IService<AppUser> {

    /**
     * 用户注册。
     * <p>先校验用户名是否已存在；不存在时创建新用户，并将密码加密后持久化。</p>
     *
     * @param username 用户名，需保证全表唯一
     * @param password 原始密码，将在实现类中被加密存储
     * @param nickname 用户昵称；若为空则默认使用用户名作为昵称
     * @return 注册成功后的新用户实体，包含加密后的密码
     */
    AppUser register(String username, String password, String nickname);

    /**
     * 根据用户名查找用户。
     * <p>用于登录认证等场景，按精确匹配用户名查询。</p>
     *
     * @param username 用户名
     * @return 匹配的用户实体；不存在时返回 {@code null}
     */
    AppUser findByUsername(String username);
}
