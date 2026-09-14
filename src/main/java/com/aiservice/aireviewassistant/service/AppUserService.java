package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.AppUser;
import com.baomidou.mybatisplus.extension.service.IService;

// 用户表 服务接口
public interface AppUserService extends IService<AppUser> {

    // 用户注册（返回新用户，密码已加密）
    AppUser register(String username, String password, String nickname);

    // 根据用户名查找用户
    AppUser findByUsername(String username);
}
