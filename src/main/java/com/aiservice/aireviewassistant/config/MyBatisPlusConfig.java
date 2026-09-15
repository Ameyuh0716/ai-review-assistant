package com.aiservice.aireviewassistant.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 * <p>通过 {@link MapperScan} 指定 Mapper 接口扫描路径，Spring Boot 启动时会自动注册
 * {@code com.aiservice.aireviewassistant.mapper} 包下的所有 MyBatis Mapper。</p>
 */
@Configuration
@MapperScan("com.aiservice.aireviewassistant.mapper")
public class MyBatisPlusConfig {
}
