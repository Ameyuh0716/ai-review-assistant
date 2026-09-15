package com.aiservice.aireviewassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户注册请求 DTO。
 * <p>封装用户注册时提交的信息，包含用户名、密码与昵称，字段均带有校验规则。</p>
 */
@Getter
@Setter
public class RegisterRequest {

    /**
     * 用户名；必填。
     * <ul>
     *   <li>长度：3-30 个字符</li>
     *   <li>格式：仅允许字母、数字和下划线</li>
     * </ul>
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 30, message = "用户名长度需在 3-30 之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    /**
     * 登录密码；必填。
     * <ul>
     *   <li>长度：6-100 个字符</li>
     * </ul>
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度需在 6-100 之间")
    private String password;

    /**
     * 用户昵称；可选。
     * <ul>
     *   <li>最大长度：50 个字符</li>
     * </ul>
     */
    @Size(max = 50, message = "昵称最长 50 字")
    private String nickname;
}
