package com.aiservice.aireviewassistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户登录请求 DTO。
 * <p>封装用户登录时提交的用户名与密码，用于账号认证接口。</p>
 */
@Getter
@Setter
public class LoginRequest {

    /** 用户名；必填，不允许为空字符串。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码；必填，不允许为空字符串。 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
