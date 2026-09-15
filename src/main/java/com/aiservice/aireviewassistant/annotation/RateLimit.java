package com.aiservice.aireviewassistant.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * API 限流注解。
 * <p>
 * 标注在方法上，用于限制单位时间内的最大请求数。
 * 配合 {@link com.aiservice.aireviewassistant.aspect.RateLimitAspect} 实现基于令牌桶算法的内存级限流。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 时间窗口内的最大请求数（令牌桶容量）。
     *
     * @return 最大请求数，默认为 10
     */
    int capacity() default 10;

    /**
     * 时间窗口长度。
     *
     * @return 窗口长度，默认为 1
     */
    long duration() default 1;

    /**
     * 时间窗口单位。
     *
     * @return 时间单位，默认为 {@link TimeUnit#MINUTES}
     */
    TimeUnit unit() default TimeUnit.MINUTES;

    /**
     * 触发限流时的提示信息。
     *
     * @return 提示文本
     */
    String message() default "请求过于频繁，请稍后再试。";
}
