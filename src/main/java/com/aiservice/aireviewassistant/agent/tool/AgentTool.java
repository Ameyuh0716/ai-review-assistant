package com.aiservice.aireviewassistant.agent.tool;

import reactor.core.publisher.Flux;

/**
 * Agent 工具接口。
 * 
 * 所有可被 Agent 调用的能力单元必须实现此接口 
 * 提供统一的名称、描述、参数描述以及同步/流式两种执行方式
 * 工具可自带参数校验与结果格式化逻辑。
 */
public interface AgentTool {

    String getName();

    String getDescription();

    /**
     * 返回工具参数 Schema，以 JSON 格式描述工具所需的参数结构。
     *
     * @return 参数 Schema 字符串
     */
    String getParameterSchema();

    /**
     * 同步执行工具。
     *
     * @param context 工具执行上下文
     * @return 工具执行结果
     */
    String execute(ToolContext context);

    /**
     * 流式执行工具。
     *
     * @param context 工具执行上下文
     * @return 流式输出的结果片段
     */
    Flux<String> stream(ToolContext context);

    default boolean validate(ToolContext context) {
        return true;
    }

    default String getValidationError(ToolContext context) {
        return null;
    }

    /**
     * 对工具返回的原始结果进行对话展示层格式化。
     * 
     * 例如隐藏测验答案、压缩冗余内容等。默认直接透传，不修改内容。
     */
    default String formatForDisplay(String rawResponse, ToolContext context) {
        return rawResponse;
    }
}
