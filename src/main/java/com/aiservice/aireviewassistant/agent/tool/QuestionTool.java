package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.RagService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * RAG 问答工具。
 * <p>
 * 基于知识库检索上下文，回答与课程相关的问题；无额外参数，直接复用用户原始消息作为查询。
 */
@Component
public class QuestionTool implements AgentTool {

    /** RAG 服务，用于检索知识库并生成回答。 */
    private final RagService ragService;

    /**
     * 构造 RAG 问答工具。
     *
     * @param ragService RAG 服务
     */
    public QuestionTool(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 返回工具名称 {@code QUESTION}。
     *
     * @return 工具名称
     */
    @Override
    public String getName() {
        return "QUESTION";
    }

    /**
     * 返回工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String getDescription() {
        return "基于知识库回答课程问题";
    }

    /**
     * 返回参数 Schema；RAG 问答无需额外参数。
     *
     * @return "无"
     */
    @Override
    public String getParameterSchema() {
        return "无";
    }

    /**
     * 同步基于知识库回答问题。
     *
     * @param context 工具执行上下文
     * @return 生成的答案文本
     */
    @Override
    public String execute(ToolContext context) {
        return ragService.answerQuestion(context.getUserMessage(), context.getConversationId(),
            RagService.RagScope.of(context.getUserId(), context.getCourseId()));
    }

    /**
     * 流式基于知识库回答问题。
     * <p>首个帧为 RAG 检索元数据（{"__rag":...}），前端据此展示检索命中情况。</p>
     *
     * @param context 工具执行上下文
     * @return 流式答案片段（首帧为元数据帧）
     */
    @Override
    public Flux<String> stream(ToolContext context) {
        RagService.RagAnswer answer = ragService.answerQuestionStreamWithMeta(
            context.getUserMessage(), context.getConversationId(),
            RagService.RagScope.of(context.getUserId(), context.getCourseId()));
        return Flux.concat(
            Flux.just(answer.meta().toSseJson()),
            answer.content()
        );
    }
}
