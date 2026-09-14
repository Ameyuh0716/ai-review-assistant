package com.aiservice.aireviewassistant.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

// MyBatis-Plus 配置类
// @MapperScan 指定扫描哪个包下的 Mapper 接口
@Configuration
@MapperScan("com.aiservice.aireviewassistant.mapper")
public class MyBatisPlusConfig {
}
