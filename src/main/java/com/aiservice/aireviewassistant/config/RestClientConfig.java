package com.aiservice.aireviewassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // 设置 RestClient 超时，防止 LLM 调用长时间挂起
    // 流式生成长内容（如复习计划）可能需要较长时间
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(180));   // 3分钟，适配长内容流式生成
        return RestClient.builder().requestFactory(factory);
    }
}
