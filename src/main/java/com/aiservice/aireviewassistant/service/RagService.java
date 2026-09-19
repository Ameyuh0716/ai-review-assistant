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

    /**
     * 基于 RAG 的流式问答（带检索元数据）。
     * <p>
     * 在返回答案流的同时回传本次检索的统计数据（命中数量、TopK、阈值、耗时、是否关键词兜底等），
     * 供前端在对话中展示“RAG 执行情况”。
     * </p>
     *
     * @param question       用户问题
     * @param conversationId 当前会话 ID
     * @return 检索元数据 + 答案流
     */
    RagAnswer answerQuestionStreamWithMeta(String question, Integer conversationId);

    /**
     * 检索知识库上下文（带检索元数据）。
     *
     * @param query          查询文本
     * @param conversationId 当前会话 ID
     * @return 检索元数据 + 拼接上下文
     */
    RagContext retrieveContextWithMeta(String query, Integer conversationId);

    /**
     * 检索元数据。
     * <p>
     * 序列化为 SSE 帧（{@link #toSseJson()}）后随答案流一起推送，前端据此展示检索统计。
     * 帧以 {@code {"__rag":true,...}} 形式开头，普通文本不会与之冲突。
     * </p>
     *
     * @param resultCount         最终命中片段数
     * @param candidateCount      向量检索初始召回数（重排序前）
     * @param topK                配置的 TopK
     * @param similarityThreshold 相似度阈值
     * @param latencyMs           检索耗时（毫秒）
     * @param keywordFallback     是否触发了关键词兜底检索
     * @param topScore            最高相似度分数；无命中时为 null
     */
    record RagMeta(int resultCount, int candidateCount, int topK, double similarityThreshold,
                   long latencyMs, boolean keywordFallback, Double topScore) {

        /** SSE 元数据帧前缀，用于识别与过滤元数据帧。 */
        public static final String SSE_PREFIX = "{\"__rag\":";

        /**
         * 序列化为 SSE 元数据帧。
         *
         * @return JSON 字符串，形如 {"__rag":true,"resultCount":3,...}
         */
        public String toSseJson() {
            StringBuilder sb = new StringBuilder(SSE_PREFIX);
            sb.append("true,\"resultCount\":").append(resultCount)
              .append(",\"candidateCount\":").append(candidateCount)
              .append(",\"topK\":").append(topK)
              .append(",\"threshold\":").append(similarityThreshold)
              .append(",\"latencyMs\":").append(latencyMs)
              .append(",\"keywordFallback\":").append(keywordFallback);
            if (topScore != null) {
                sb.append(",\"topScore\":")
                  .append(String.format(java.util.Locale.ROOT, "%.4f", topScore));
            }
            return sb.append("}").toString();
        }
    }

    /**
     * 带检索元数据的流式问答结果。
     *
     * @param meta    检索元数据
     * @param content 答案流
     */
    record RagAnswer(RagMeta meta, Flux<String> content) {}

    /**
     * 带检索元数据的上下文检索结果。
     *
     * @param meta    检索元数据
     * @param context 拼接后的知识库上下文
     */
    record RagContext(RagMeta meta, String context) {}
}
