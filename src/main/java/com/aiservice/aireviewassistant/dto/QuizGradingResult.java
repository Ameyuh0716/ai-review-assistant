package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 答题批改结果响应 DTO。
 * <p>用户提交答题后，由后端对答案进行批改并返回总览统计以及每道题目的详细批改信息。</p>
 */
@Getter
@Setter
public class QuizGradingResult {

    /** 总题数。 */
    private int totalQuestions;

    /** 答对题数。 */
    private int correctCount;

    /** 答错题数。 */
    private int wrongCount;

    /** 正确率，取值范围 0-100。 */
    private int accuracyRate;

    /** 各题详细批改结果列表。 */
    private List<QuestionResult> results;

    /**
     * 单道题目批改详情内部 DTO。
     */
    @Getter
    @Setter
    public static class QuestionResult {

        /** 题号，从 1 开始。 */
        private Integer questionNo;

        /** 题目内容。 */
        private String question;

        /** 用户提交的答案。 */
        private String userAnswer;

        /** 正确答案。 */
        private String correctAnswer;

        /** 是否答对；true 表示正确，false 表示错误。 */
        private boolean correct;

        /** 答案解析说明。 */
        private String explanation;
    }
}
