package com.aiservice.aireviewassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * REST 客户端配置类。
 * <p>配置 {@link RestClient} 的全局超时策略，防止调用 LLM 等外部服务时长时间挂起。</p>
 */
@Configuration
public class RestClientConfig {

    /**
     * 注册配置好超时的 {@link RestClient.Builder} Bean。
     * <p>连接超时设置为 10 秒；读取超时设置为 180 秒（3 分钟），以适配复习计划等长内容流式生成场景。</p>
     *
     * @return 配置后的 RestClient.Builder 实例
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // 3 分钟读取超时，适配长内容流式生成
        factory.setReadTimeout(Duration.ofSeconds(180));
        return RestClient.builder().requestFactory(factory);
    }
}
