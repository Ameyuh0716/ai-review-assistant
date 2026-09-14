package com.aiservice.aireviewassistant.service;

import reactor.core.publisher.Flux;

// RAG检索增强服务接口
public interface RagService {

    // 普通问答（一次性返回）
    String answerQuestion(String question, Integer conversationId);

    // 流式问答（SSE推送）
    Flux<String> answerQuestionStream(String question, Integer conversationId);

    // 检索与问题相关的知识库上下文，用于总结/解释等场景
    String retrieveContext(String query, Integer conversationId);
}
