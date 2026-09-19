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

/**
 * ChatClient 统一配置类。
 * <p>根据 {@code ai.model.*} 配置构建主 {@link ChatClient}，支持 dashscope、openai、ollama 三种供应商。
 * 通过 {@link Primary} 标记，确保该 Bean 作为默认 ChatClient 被注入。</p>
 */
@Configuration
public class ChatClientConfig {

    private final ModelProperties modelProperties;

    /**
     * 构造 ChatClient 配置类。
     *
     * @param modelProperties AI 模型配置属性
     */
    public ChatClientConfig(ModelProperties modelProperties) {
        this.modelProperties = modelProperties;
    }

    /**
     * 注册主 {@link ChatClient} Bean。
     * <p>根据 {@link ModelProperties#getProvider()} 的值选择对应的构建方式：
     * <ul>
     *   <li>dashscope：使用 Spring AI Alibaba 自动注入的 Builder</li>
     *   <li>openai：手动创建 {@code OpenAiApi} 与 {@code OpenAiChatModel}</li>
     *   <li>ollama：手动创建 {@code OllamaApi} 与 {@code OllamaChatModel}</li>
     * </ul>
     * 若配置了不支持的供应商，则抛出异常。</p>
     *
     * @param builder Spring AI 自动注入的 ChatClient.Builder
     * @return 配置完成的 ChatClient 实例
     * @throws IllegalArgumentException 当供应商不是 dashscope/openai/ollama 时抛出
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder builder) {
        return buildClient(builder, modelProperties.getName());
    }

    /**
     * 注册快速模型 {@link ChatClient} Bean（Bean 名 {@code fastChatClient}）。
     * <p>用于对响应速度敏感、对措辞要求不高的场景（如批量出题）。
     * 模型名取自 {@code ai.model.fast-name}，为空时回退到主模型名。</p>
     *
     * @param builder Spring AI 自动注入的 ChatClient.Builder
     * @return 使用快速模型的 ChatClient 实例
     */
    @Bean("fastChatClient")
    public ChatClient fastChatClient(ChatClient.Builder builder) {
        String fastName = modelProperties.getFastName();
        if (fastName == null || fastName.isBlank()) {
            fastName = modelProperties.getName();
        }
        return buildClient(builder, fastName);
    }

    /**
     * 按配置的供应商构建指定模型的 ChatClient。
     *
     * @param builder   Spring AI 自动注入的 ChatClient.Builder
     * @param modelName 模型名称
     * @return ChatClient 实例
     */
    private ChatClient buildClient(ChatClient.Builder builder, String modelName) {
        String provider = modelProperties.getProvider().toLowerCase();
        // 根据模型供应商选择对应的 ChatClient 构建策略
        return switch (provider) {
            case "dashscope" -> buildDashScopeClient(builder, modelName);
            case "openai" -> buildOpenAiClient(modelName);
            case "ollama" -> buildOllamaClient(modelName);
            default -> throw new IllegalArgumentException(
                "不支持的模型供应商: " + provider + "，请配置 ai.model.provider=dashscope|openai|ollama");
        };
    }

    /**
     * 构建 DashScope（通义千问）ChatClient。
     * <p>直接使用 Spring AI Alibaba 自动注入的 {@link ChatClient.Builder}，
     * 并设置模型名称与温度参数。</p>
     *
     * @param builder   自动注入的 ChatClient.Builder
     * @param modelName 模型名称
     * @return DashScope ChatClient 实例
     */
    private ChatClient buildDashScopeClient(ChatClient.Builder builder, String modelName) {
        return builder
            .defaultOptions(ChatOptions.builder()
                .model(modelName)
                .temperature(modelProperties.getTemperature())
                .build())
            .build();
    }

    /**
     * 构建 OpenAI ChatClient。
     * <p>手动创建 {@code OpenAiApi} 与 {@code OpenAiChatModel}；若未配置 API Key 则抛出异常。
     * 当配置了 {@code ai.model.base-url} 时，会将其设置为自定义基础 URL（可用于代理）。</p>
     *
     * @param modelName 模型名称
     * @return OpenAI ChatClient 实例
     * @throws IllegalArgumentException 当未配置 api-key 时抛出
     */
    private ChatClient buildOpenAiClient(String modelName) {
        if (modelProperties.getApiKey() == null || modelProperties.getApiKey().isEmpty()) {
            throw new IllegalArgumentException("OpenAI 模型需要配置 ai.model.api-key");
        }
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
            .apiKey(modelProperties.getApiKey());
        // 仅当显式配置了 base-url 时才覆盖默认端点
        String baseUrl = modelProperties.getBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            apiBuilder.baseUrl(baseUrl);
        }
        OpenAiApi openAiApi = apiBuilder.build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .defaultOptions(OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(modelProperties.getTemperature())
                .build())
            .build();
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 构建 Ollama ChatClient。
     * <p>手动创建 {@code OllamaApi} 与 {@code OllamaChatModel}；
     * 若未配置 {@code ai.model.base-url}，则默认使用本地 Ollama 地址
     * {@code http://localhost:11434}。</p>
     *
     * @param modelName 模型名称
     * @return Ollama ChatClient 实例
     */
    private ChatClient buildOllamaClient(String modelName) {
        // 未配置 base-url 时回退到本地 Ollama 默认端口
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
                .model(modelName)
                .temperature(modelProperties.getTemperature())
                .build())
            .build();
        return ChatClient.builder(chatModel).build();
    }
}
