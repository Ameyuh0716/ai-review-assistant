package com.aiservice.aireviewassistant.service;

import reactor.core.publisher.Flux;

// 题目生成服务接口
public interface QuizService {

    // 根据知识点生成一道选择题
    String generateQuiz(String topic);

    // 根据知识点生成指定数量的题目
    String generateQuiz(String topic, int count);

    // 流式生成题目
    Flux<String> generateQuizStream(String topic, int count);

    // 将模型原始输出格式化为标准 Markdown（包含答案/解析）
    String formatQuizOutput(String raw, String topic);

    // 将模型原始输出格式化为供对话展示的无答案 Markdown（隐藏答案与解析）
    String formatQuizOutputForChat(String raw, String topic);
}
