package com.aiservice.aireviewassistant.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局共享配置类。
 * <p>负责注册应用级 Bean，目前提供统一的 {@link ObjectMapper}，供序列化/反序列化使用。</p>
 */
@Configuration
public class AppConfig {

    /**
     * 注册全局共享的 {@link ObjectMapper} Bean。
     * <p>配置内容：注册 JDK8 日期时间模块、将日期格式化为字符串、忽略未知 JSON 字段。
     * 避免各 Service 重复创建 ObjectMapper 实例。</p>
     *
     * @return 配置后的 ObjectMapper 实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
