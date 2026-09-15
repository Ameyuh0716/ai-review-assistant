package com.aiservice.aireviewassistant.aspect;

import com.aiservice.aireviewassistant.annotation.RateLimit;
import com.aiservice.aireviewassistant.exception.TooManyRequestsException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 限流切面。
 * <p>
 * 基于 Bucket4j 令牌桶算法实现方法级内存限流。
 * 每个被 {@link RateLimit} 注解的方法会维护一个独立的令牌桶，
 * 桶的维度为方法签名，不支持按用户/IP 等维度隔离。
 * </p>
 */
@Aspect
@Component
public class RateLimitAspect {

    /**
     * 方法签名到令牌桶的缓存。
     * 使用 {@link ConcurrentHashMap} 保证并发安全，避免多线程下重复创建 Bucket。
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 环绕通知：在目标方法执行前尝试从令牌桶消费一个令牌。
     *
     * @param joinPoint 连接点，包含被拦截方法的信息
     * @param rateLimit 方法上的 {@link RateLimit} 注解配置
     * @return 目标方法的返回值
     * @throws Throwable 当目标方法抛出异常或令牌不足时抛出限流异常
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 使用方法长签名作为限流维度，确保同一方法共享同一个令牌桶
        String key = joinPoint.getSignature().toLongString();
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(rateLimit));

        // 尝试消费 1 个令牌：成功则放行，失败则抛出 TooManyRequestsException
        if (bucket.tryConsume(1)) {
            return joinPoint.proceed();
        }
        throw new TooManyRequestsException(rateLimit.message());
    }

    /**
     * 根据 {@link RateLimit} 注解创建令牌桶。
     * <p>
     * 采用 interval refill 策略：在每个时间窗口开始时一次性补齐全部令牌。
     * </p>
     *
     * @param rateLimit 限流注解配置
     * @return 配置好的 {@link Bucket} 实例
     */
    private Bucket createBucket(RateLimit rateLimit) {
        Bandwidth bandwidth = Bandwidth.builder()
            .capacity(rateLimit.capacity())
            .refillIntervally(rateLimit.capacity(), Duration.of(rateLimit.duration(), rateLimit.unit().toChronoUnit()))
            .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
