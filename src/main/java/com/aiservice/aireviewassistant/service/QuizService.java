package com.aiservice.aireviewassistant.service;

import reactor.core.publisher.Flux;

/**
 * 题目生成服务接口。
 * <p>
 * 负责根据指定知识点调用大模型生成单选/多选等练习题，并将模型原始输出统一格式化为标准 Markdown。
 * 提供同步生成、批量生成、流式生成以及面向对话场景隐藏答案的格式化能力。
 * </p>
 */
public interface QuizService {

    /**
     * 根据知识点生成一道选择题。
     *
     * @param topic 知识点主题，例如 "操作系统-进程同步"
     * @return 格式化后的 Markdown 题目文本，包含题干、选项、答案与解析
     */
    String generateQuiz(String topic);

    /**
     * 根据知识点生成指定数量的题目。
     *
     * @param topic 知识点主题
     * @param count 期望生成的题目数量（至少为 1）
     * @return 格式化后的 Markdown 题目文本
     */
    String generateQuiz(String topic, int count);

    /**
     * 以流式方式生成题目。
     * <p>
     * 内部先让模型以 SSE 方式返回完整内容，再由后端统一格式化后一次性发出，避免前端按字符渲染时丢失换行与缩进。
     * </p>
     *
     * @param topic 知识点主题
     * @param count 期望生成的题目数量
     * @return 格式化后题目文本的响应式流
     */
    Flux<String> generateQuizStream(String topic, int count);

    /**
     * 以流式方式生成题目（实时预览 + 最终格式化帧）。
     * <p>
     * 与 {@link #generateQuizStream} 的区别：模型输出的原始 token 会实时推送（前端立即看到生成过程，
     * 显著改善等待体验），全部完成后追加一帧 JSON 控制帧
     * {@code {"__quizFinal":true,"content":"<格式化后的标准 Markdown>"}}，
     * 前端据此解析出结构化题目。
     * </p>
     *
     * @param topic 知识点主题
     * @param count 期望生成的题目数量
     * @return 实时 token 流 + 末尾 JSON 格式化帧
     */
    Flux<String> generateQuizStreamWithFinal(String topic, int count);

    /**
     * 将模型原始输出格式化为标准 Markdown（包含答案/解析）。
     *
     * @param raw   模型返回的原始文本
     * @param topic 知识点主题，用于填充学科名称与兜底
     * @return 规范化后的 Markdown 题目文本
     */
    String formatQuizOutput(String raw, String topic);

    /**
     * 将模型原始输出格式化为供对话展示的无答案 Markdown（隐藏答案与解析）。
     *
     * @param raw   模型返回的原始文本
     * @param topic 知识点主题
     * @return 隐藏答案与解析后的 Markdown 题目文本
     */
    String formatQuizOutputForChat(String raw, String topic);
}
