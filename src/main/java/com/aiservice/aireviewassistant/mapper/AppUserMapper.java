package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.AppUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code app_user}，实体类型为 {@link AppUser}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于存储注册用户的账号、密码、昵称、头像及角色等基本信息。
 * </p>
 */
public interface AppUserMapper extends BaseMapper<AppUser> {
}
