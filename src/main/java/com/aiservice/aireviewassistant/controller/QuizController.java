package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.dto.QuizGradingResult;
import com.aiservice.aireviewassistant.dto.QuizSubmitRequest;
import com.aiservice.aireviewassistant.service.QuizService;
import com.aiservice.aireviewassistant.service.WrongAnswerBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

// 题目生成 + 答题批改控制器
@Tag(name = "练习题", description = "生成题目、提交答案、自动批改")
@Validated
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;
    private final WrongAnswerBookService wrongAnswerBookService;

    public QuizController(QuizService quizService, WrongAnswerBookService wrongAnswerBookService) {
        this.quizService = quizService;
        this.wrongAnswerBookService = wrongAnswerBookService;
    }

    // 生成一道选择题
    @Operation(summary = "生成一道练习题")
    @PostMapping
    public String generateQuiz(@RequestParam("topic") @NotBlank(message = "知识点不能为空") String topic) {
        return quizService.generateQuiz(topic);
    }

    // 生成多道选择题
    @Operation(summary = "生成多道练习题")
    @PostMapping("/generate")
    public String generateMultipleQuiz(
            @RequestParam("topic") @NotBlank(message = "知识点不能为空") String topic,
            @RequestParam(value = "count", defaultValue = "1") @Min(1) @Max(10) int count) {
        return quizService.generateQuiz(topic, count);
    }

    // 提交答案并批改
    @Operation(summary = "提交答案并自动批改")
    @PostMapping("/grade")
    public ApiResponse<QuizGradingResult> gradeQuiz(@RequestBody QuizSubmitRequest request,
                                                     @RequestAttribute(required = false) Integer currentUserId) {
        QuizGradingResult result = new QuizGradingResult();
        List<QuizGradingResult.QuestionResult> results = new ArrayList<>();
        int correctCount = 0;

        if (request.getAnswers() != null) {
            for (QuizSubmitRequest.AnswerItem item : request.getAnswers()) {
                QuizGradingResult.QuestionResult qr = new QuizGradingResult.QuestionResult();
                qr.setQuestionNo(item.getQuestionNo());
                qr.setQuestion(item.getQuestion());
                qr.setUserAnswer(item.getUserAnswer());
                qr.setCorrectAnswer(item.getCorrectAnswer());

                boolean correct = item.getCorrectAnswer() != null
                    && item.getCorrectAnswer().equalsIgnoreCase(item.getUserAnswer());
                qr.setCorrect(correct);
                qr.setExplanation(item.getExplanation());

                if (correct) {
                    correctCount++;
                } else if (currentUserId != null) {
                    // 答错的题目自动加入错题本
                    wrongAnswerBookService.recordWrong(
                        currentUserId, request.getCourseId(),
                        item.getQuestion(), item.getOptions(),
                        item.getCorrectAnswer(), item.getUserAnswer(),
                        item.getExplanation(), request.getTopic()
                    );
                }
                results.add(qr);
            }
        }

        result.setTotalQuestions(results.size());
        result.setCorrectCount(correctCount);
        result.setWrongCount(results.size() - correctCount);
        result.setAccuracyRate(results.size() > 0 ? Math.round(correctCount * 100f / results.size()) : 0);
        result.setResults(results);

        return ApiResponse.success(result);
    }
}
