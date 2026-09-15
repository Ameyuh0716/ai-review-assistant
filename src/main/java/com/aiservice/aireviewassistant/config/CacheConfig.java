package com.aiservice.aireviewassistant.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 本地缓存配置类。
 * <p>启用 Spring 缓存抽象，并使用 Caffeine 作为本地缓存实现，用于降低重复的 LLM 调用与向量检索开销。
 * 主要缓存 Agent 工具结果（出题、复习计划、闲聊等）。</p>
 */
@EnableCaching
@Configuration
public class CacheConfig {

    /**
     * 注册 Caffeine {@link CacheManager} Bean。
     * <p>配置策略：最大缓存条目数 1000，写入后 10 分钟过期，并开启统计功能。</p>
     *
     * @return CaffeineCacheManager 实例
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats());
        return manager;
    }
}
