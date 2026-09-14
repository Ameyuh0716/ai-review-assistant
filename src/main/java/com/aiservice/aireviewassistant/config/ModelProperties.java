package com.aiservice.aireviewassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// AI 模型配置属性：支持切换不同模型供应商和模型名称
@Component
@ConfigurationProperties(prefix = "ai.model")
public class ModelProperties {

    // 模型供应商：dashscope、openai、ollama 等
    private String provider = "dashscope";

    // 模型名称
    private String name = "qwen-plus";

    // 温度参数
    private Double temperature = 0.9;

    // API Key（OpenAI / DashScope 等需要）
    private String apiKey;

    // 基础 URL（Ollama / OpenAI 代理需要）
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
