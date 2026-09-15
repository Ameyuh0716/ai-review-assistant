package com.aiservice.aireviewassistant.common;

import java.time.LocalDateTime;

/**
 * 统一 API 响应体。
 * <p>
 * 所有 Controller 接口统一返回该对象，便于前端按固定结构解析结果。
 * code 为 0 表示成功，非 0 表示失败；timestamp 为响应生成时间。
 * </p>
 *
 * @param <T> 响应数据的类型
 */
public class ApiResponse<T> {

    /** 状态码：0 表示成功，非 0 表示失败 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 响应时间戳 */
    private LocalDateTime timestamp;

    /**
     * 默认构造方法。
     * 初始化当前时间戳。
     */
    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 全参数构造方法。
     *
     * @param code    状态码
     * @param message 提示信息
     * @param data    响应数据
     */
    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 构造成功响应。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return code 为 0 的成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    /**
     * 构造带自定义消息的成功响应。
     *
     * @param message 自定义成功提示
     * @param data    响应数据
     * @param <T>     响应数据类型
     * @return code 为 0 的成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(0, message, data);
    }

    /**
     * 构造默认失败响应。
     *
     * @param message 错误提示
     * @param <T>     响应数据类型
     * @return code 为 500 的失败响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }

    /**
     * 构造指定状态码的失败响应。
     *
     * @param code    状态码
     * @param message 错误提示
     * @param <T>     响应数据类型
     * @return 指定 code 的失败响应
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
