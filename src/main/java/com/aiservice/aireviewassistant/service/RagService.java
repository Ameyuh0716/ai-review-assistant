package com.aiservice.aireviewassistant.service;

import reactor.core.publisher.Flux;

/**
 * RAG（检索增强生成）服务接口。
 * <p>
 * 负责将用户问题与课程知识库进行向量检索，并将检索到的上下文注入大模型提示词，
 * 以提升问答、总结、解释等场景的答案准确性与可溯源性。
 * 支持一次性同步回答与 Server-Sent Events（SSE）流式回答两种调用方式。
 * </p>
 */
public interface RagService {

    /**
     * 基于 RAG 的同步问答：一次性返回答案文本。
     *
     * @param question       用户问题
     * @param conversationId 当前会话 ID，用于检索日志关联；允许为空
     * @return 大模型生成的回答内容；未命中知识库时直接由大模型回答
     */
    String answerQuestion(String question, Integer conversationId);

    /**
     * 基于 RAG 的流式问答：通过 SSE 逐字推送回答内容。
     *
     * @param question       用户问题
     * @param conversationId 当前会话 ID，用于检索日志关联；允许为空
     * @return 按 Token 流式返回的字符串流
     */
    Flux<String> answerQuestionStream(String question, Integer conversationId);

    /**
     * 检索与问题相关的知识库上下文，返回拼接后的文本块。
     * <p>
     * 主要用于总结、解释、知识点提炼等不直接生成答案的场景，
     * 让调用方自行决定如何使用检索结果。
     * </p>
     *
     * @param query          查询文本
     * @param conversationId 当前会话 ID，用于检索日志关联；允许为空
     * @return 拼接后的知识库上下文；无命中时返回空字符串
     */
    String retrieveContext(String query, Integer conversationId);
}
