package com.aiservice.aireviewassistant.agent.tool;

import reactor.core.publisher.Flux;

/**
 * Agent 工具接口。
 * <p>
 * 所有可被 Agent 调用的能力单元必须实现此接口，提供统一的名称、描述、参数描述以及
 * 同步/流式两种执行方式。工具可自带参数校验与结果格式化逻辑。
 */
public interface AgentTool {

    /**
     * 返回工具名称，作为 Agent 识别与路由工具的唯一标识。
     *
     * @return 工具名称
     */
    String getName();

    /**
     * 返回工具描述，用于向大模型说明工具的用途与适用场景。
     *
     * @return 工具描述
     */
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

    /**
     * 校验当前上下文是否满足工具的调用条件。
     * <p>
     * 默认实现直接放行，具体工具可覆盖以执行业务校验。
     *
     * @param context 工具执行上下文
     * @return 校验通过返回 {@code true}，否则返回 {@code false}
     */
    default boolean validate(ToolContext context) {
        return true;
    }

    /**
     * 获取校验失败时的错误提示信息。
     *
     * @param context 工具执行上下文
     * @return 校验失败提示；校验通过或无提示时返回 {@code null}
     */
    default String getValidationError(ToolContext context) {
        return null;
    }

    /**
     * 对工具返回的原始结果进行对话展示层格式化。
     * <p>
     * 例如隐藏测验答案、压缩冗余内容等。默认直接透传，不修改内容。
     *
     * @param rawResponse 工具原始输出
     * @param context 工具执行上下文
     * @return 格式化后的展示文本
     */
    default String formatForDisplay(String rawResponse, ToolContext context) {
        return rawResponse;
    }
}
