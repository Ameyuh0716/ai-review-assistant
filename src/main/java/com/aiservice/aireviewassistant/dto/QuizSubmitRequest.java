package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 答题提交请求 DTO。
 * <p>用户完成测验后，前端通过该对象将课程、主题以及各题的作答信息提交给后端进行批改。</p>
 */
@Getter
@Setter
public class QuizSubmitRequest {

    /** 课程 ID，标识测验所属课程。 */
    private Integer courseId;

    /** 知识点主题，用于定位测验考查的知识范围。 */
    private String topic;

    /** 用户提交的答案列表，包含每道题目的作答与题目信息。 */
    private List<AnswerItem> answers;

    /**
     * 单道题目作答项内部 DTO。
     */
    @Getter
    @Setter
    public static class AnswerItem {

        /** 题号，从 1 开始。 */
        private Integer questionNo;

        /** 题目内容文本。 */
        private String question;

        /**
         * 选项文本；多行字符串格式，例如：
         * <pre>A. xxx
         * B. xxx
         * C. xxx
         * D. xxx</pre>
         */
        private String options;

        /** 用户选择的答案，例如 "A"。 */
        private String userAnswer;

        /** 该题正确答案，例如 "B"。 */
        private String correctAnswer;

        /** 该题答案解析。 */
        private String explanation;
    }
}
