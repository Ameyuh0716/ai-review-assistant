package com.aiservice.aireviewassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置类。
 * <p>注册 {@link OpenAPI} Bean，提供 API 基础信息（标题、描述、版本、联系人）。</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * 注册 OpenAPI 文档 Bean。
     *
     * @return 配置完成的 OpenAPI 实例
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("AI 复习助手 API")
                .description("基于 Agent 架构的智能复习助手：支持出题、复习计划、RAG 问答、知识库管理、进度统计等能力")
                .version("v1.0.0")
                .contact(new Contact().name("AI Review Assistant Team")));
    }
}
