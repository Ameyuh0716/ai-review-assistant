package com.aiservice.aireviewassistant.exception;

/**
 * 业务异常。
 * <p>
 * 用于表达可预期的业务规则错误（如参数不合法、资源不存在等），
 * 由 {@link GlobalExceptionHandler} 统一捕获并包装为 {@link com.aiservice.aireviewassistant.common.ApiResponse} 返回给前端。
 * </p>
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码，默认 400 */
    private final int code;

    /**
     * 使用默认错误码 400 构造业务异常。
     *
     * @param message 错误提示信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /**
     * 使用指定错误码构造业务异常。
     *
     * @param code    业务错误码
     * @param message 错误提示信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码。
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }
}
