package com.aiservice.aireviewassistant.agent.tool;

import reactor.core.publisher.Flux;

// Agent 工具接口：所有工具必须实现该接口
public interface AgentTool {

    // 工具名称（唯一标识）
    String getName();

    // 工具描述
    String getDescription();

    // 工具参数 Schema（JSON 格式描述）
    String getParameterSchema();

    // 同步执行工具
    String execute(ToolContext context);

    // 流式执行工具
    Flux<String> stream(ToolContext context);

    // 参数校验：返回 true 表示校验通过
    default boolean validate(ToolContext context) {
        return true;
    }

    // 校验失败时的错误提示
    default String getValidationError(ToolContext context) {
        return null;
    }

    // 对工具返回的原始结果做对话展示层的格式化（例如隐藏测验答案）。
    // 默认直接透传，不修改内容。
    default String formatForDisplay(String rawResponse, ToolContext context) {
        return rawResponse;
    }
}
