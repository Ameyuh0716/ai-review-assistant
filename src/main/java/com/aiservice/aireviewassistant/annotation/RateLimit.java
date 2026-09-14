package com.aiservice.aireviewassistant.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

// API 限流注解：限制单位时间内的最大请求数
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    // 时间窗口内的最大请求数
    int capacity() default 10;

    // 时间窗口长度
    long duration() default 1;

    // 时间单位
    TimeUnit unit() default TimeUnit.MINUTES;

    // 提示信息
    String message() default "请求过于频繁，请稍后再试。";
}
