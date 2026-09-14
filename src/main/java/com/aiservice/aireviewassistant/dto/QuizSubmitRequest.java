package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 答题提交请求 DTO
@Getter
@Setter
public class QuizSubmitRequest {

    // 课程ID
    private Integer courseId;

    // 知识点主题
    private String topic;

    // 用户提交的答案列表
    private List<AnswerItem> answers;

    @Getter
    @Setter
    public static class AnswerItem {
        // 题号（从1开始）
        private Integer questionNo;
        // 题目内容
        private String question;
        // 选项（如 "A. xxx\nB. xxx\nC. xxx\nD. xxx"）
        private String options;
        // 用户选择的答案（如 "A"）
        private String userAnswer;
        // 正确答案
        private String correctAnswer;
        // 解析
        private String explanation;
    }
}
