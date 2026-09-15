package com.aiservice.aireviewassistant.exception;

/**
 * 请求过于频繁异常。
 * <p>
 * 当单位时间内请求数超过 {@link com.aiservice.aireviewassistant.annotation.RateLimit}
 * 设定的阈值时，由 {@link com.aiservice.aireviewassistant.aspect.RateLimitAspect} 抛出。
 * {@link GlobalExceptionHandler} 会将其映射为 HTTP 429 响应。
 * </p>
 */
public class TooManyRequestsException extends RuntimeException {

    /**
     * 构造限流异常。
     *
     * @param message 提示信息
     */
    public TooManyRequestsException(String message) {
        super(message);
    }
}
