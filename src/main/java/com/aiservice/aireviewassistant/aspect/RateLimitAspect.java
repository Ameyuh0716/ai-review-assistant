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

// API 限流 AOP：基于 Bucket4j 实现内存级令牌桶限流
@Aspect
@Component
public class RateLimitAspect {

    // 按方法路径缓存令牌桶
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = joinPoint.getSignature().toLongString();
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(rateLimit));

        if (bucket.tryConsume(1)) {
            return joinPoint.proceed();
        }
        throw new TooManyRequestsException(rateLimit.message());
    }

    // 根据注解配置创建令牌桶
    private Bucket createBucket(RateLimit rateLimit) {
        Bandwidth bandwidth = Bandwidth.builder()
            .capacity(rateLimit.capacity())
            .refillIntervally(rateLimit.capacity(), Duration.of(rateLimit.duration(), rateLimit.unit().toChronoUnit()))
            .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }
}
