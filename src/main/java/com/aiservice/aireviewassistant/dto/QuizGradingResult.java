package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

// 答题批改结果 DTO
@Getter
@Setter
public class QuizGradingResult {

    // 总题数
    private int totalQuestions;

    // 正确数
    private int correctCount;

    // 错误数
    private int wrongCount;

    // 正确率（0-100）
    private int accuracyRate;

    // 各题详细结果
    private List<QuestionResult> results;

    @Getter
    @Setter
    public static class QuestionResult {
        private Integer questionNo;
        private String question;
        private String userAnswer;
        private String correctAnswer;
        private boolean correct;
        private String explanation;
    }
}
