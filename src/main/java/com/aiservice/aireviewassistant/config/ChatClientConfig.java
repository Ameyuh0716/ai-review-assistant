package com.aiservice.aireviewassistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

// ChatClient 统一配置：根据 ai.model.* 配置构建主 ChatClient，支持 dashscope / openai / ollama
@Configuration
public class ChatClientConfig {

    private final ModelProperties modelProperties;

    public ChatClientConfig(ModelProperties modelProperties) {
        this.modelProperties = modelProperties;
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        String provider = modelProperties.getProvider().toLowerCase();
        return switch (provider) {
            case "dashscope" -> buildDashScopeClient(builder);
            case "openai" -> buildOpenAiClient();
            case "ollama" -> buildOllamaClient();
            default -> throw new IllegalArgumentException(
                "不支持的模型供应商: " + provider + "，请配置 ai.model.provider=dashscope|openai|ollama");
        };
    }

    // DashScope：使用 Spring AI Alibaba 自动注入的 Builder
    private ChatClient buildDashScopeClient(ChatClient.Builder builder) {
        return builder
            .defaultOptions(ChatOptions.builder()
                .model(modelProperties.getName())
                .temperature(modelProperties.getTemperature())
                .build())
            .build();
    }

    // OpenAI：手动创建 ChatModel 和 ChatClient
    private ChatClient buildOpenAiClient() {
        if (modelProperties.getApiKey() == null || modelProperties.getApiKey().isEmpty()) {
            throw new IllegalArgumentException("OpenAI 模型需要配置 ai.model.api-key");
        }
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
            .apiKey(modelProperties.getApiKey());
        String baseUrl = modelProperties.getBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            apiBuilder.baseUrl(baseUrl);
        }
        OpenAiApi openAiApi = apiBuilder.build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(OpenAiChatOptions.builder()
                .model(modelProperties.getName())
                .temperature(modelProperties.getTemperature())
                .build())
            .build();
        return ChatClient.builder(chatModel).build();
    }

    // Ollama：手动创建 ChatModel 和 ChatClient
    private ChatClient buildOllamaClient() {
        String baseUrl = modelProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:11434";
        }
        OllamaApi ollamaApi = OllamaApi.builder()
            .baseUrl(baseUrl)
            .build();
        OllamaChatModel chatModel = OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .defaultOptions(OllamaOptions.builder()
                .model(modelProperties.getName())
                .temperature(modelProperties.getTemperature())
                .build())
            .build();
        return ChatClient.builder(chatModel).build();
    }
}
