package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户表。
 * <p>
 * 存储注册用户的账号、密码、昵称、头像及角色等基本信息，支持用户登录与权限区分。
 * </p>
 */
@Getter
@Setter
@TableName("app_user")
public class AppUser {

    /** 用户主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 登录用户名，系统中唯一。 */
    @TableField("username")
    private String username;

    /** 登录密码，通常为加密后的密文。 */
    @TableField("password")
    private String password;

    /** 用户昵称，用于界面展示。 */
    @TableField("nickname")
    private String nickname;

    /** 用户头像 URL 或本地路径。 */
    @TableField("avatar")
    private String avatar;

    /** 用户角色，如 admin、user 等，用于权限控制。 */
    @TableField("role")
    private String role;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 记录最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
