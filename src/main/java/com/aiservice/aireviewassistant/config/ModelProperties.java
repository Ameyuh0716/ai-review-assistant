package com.aiservice.aireviewassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模型配置属性类。
 * <p>绑定前缀为 {@code ai.model} 的配置项，用于切换不同的模型供应商、模型名称及连接参数。</p>
 */
@Component
@ConfigurationProperties(prefix = "ai.model")
public class ModelProperties {

    /** 模型供应商，默认 {@code dashscope}；可选值：dashscope、openai、ollama。 */
    private String provider = "dashscope";

    /** 模型名称，默认 {@code qwen-plus}。 */
    private String name = "qwen-plus";

    /**
     * 快速模型名称，默认 {@code qwen-turbo}。
     * <p>用于对响应速度敏感、对措辞要求不高的场景（如批量出题），速度约为 qwen-plus 的 4-5 倍。
     * 为空时回退到 {@link #name}。</p>
     */
    private String fastName = "qwen-turbo";

    /** 模型温度参数，默认 {@code 0.9}；值越大生成结果越随机。 */
    private Double temperature = 0.9;

    /** API Key，OpenAI / DashScope 等云端供应商需要配置。 */
    private String apiKey;

    /** 基础 URL，用于 Ollama 本地服务或 OpenAI 代理场景。 */
    private String baseUrl;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFastName() {
        return fastName;
    }

    public void setFastName(String fastName) {
        this.fastName = fastName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
