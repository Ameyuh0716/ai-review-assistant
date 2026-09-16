package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.dto.QuizGradingResult;
import com.aiservice.aireviewassistant.dto.QuizSubmitRequest;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.QuizService;
import com.aiservice.aireviewassistant.service.WrongAnswerBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 练习题控制器。
 * <p>负责基于知识点生成选择题、接收用户提交的答案并自动批改，
 * 同时对于已登录用户，会自动将答错的题目归入错题本。</p>
 */
@Tag(name = "练习题", description = "生成题目、提交答案、自动批改")
@Validated
@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;
    private final WrongAnswerBookService wrongAnswerBookService;
    private final CoursesService coursesService;

    /**
     * 构造方法，注入题目生成服务、错题本服务与课程服务。
     *
     * @param quizService            题目生成服务
     * @param wrongAnswerBookService 错题本服务，用于记录答错的题目
     * @param coursesService         课程查询服务
     */
    public QuizController(QuizService quizService, WrongAnswerBookService wrongAnswerBookService,
                          CoursesService coursesService) {
        this.quizService = quizService;
        this.wrongAnswerBookService = wrongAnswerBookService;
        this.coursesService = coursesService;
    }

    /**
     * 根据知识点生成一道练习题。
     * <p>HTTP: {@code POST /api/quiz?topic=xxx}</p>
     *
     * @param topic 知识点描述，不能为空
     * @return 生成的题目文本（通常为 JSON 格式字符串）
     */
    @Operation(summary = "生成一道练习题")
    @PostMapping
    public ApiResponse<String> generateQuiz(
            @RequestParam("topic") @NotBlank(message = "知识点不能为空") String topic) {
        return ApiResponse.success(quizService.generateQuiz(topic));
    }

    /**
     * 根据课程与知识点生成多道练习题。
     * <p>HTTP: {@code POST /api/quiz/generate}</p>
     * <p>请求体：{@code {"courseId": 1, "topic": "进程同步", "count": 5}}</p>
     *
     * @param body 包含 courseId、topic、count 的 JSON 请求体
     * @return 生成的多道题目文本
     */
    @Operation(summary = "生成多道练习题")
    @PostMapping("/generate")
    public ApiResponse<String> generateMultipleQuiz(@RequestBody Map<String, Object> body) {
        Object courseIdObj = body.get("courseId");
        if (courseIdObj == null) {
            return ApiResponse.error(400, "课程ID不能为空");
        }
        Integer courseId = Integer.valueOf(String.valueOf(courseIdObj));
        Courses course = coursesService.getById(courseId);
        if (course == null) {
            return ApiResponse.error(404, "课程不存在");
        }
        Object topicObj = body.get("topic");
        String topic = topicObj != null ? String.valueOf(topicObj) : course.getName();
        int count = 1;
        Object countObj = body.get("count");
        if (countObj != null) {
            try {
                count = Integer.parseInt(String.valueOf(countObj));
            } catch (NumberFormatException ignored) {
                // 非法数量使用默认值 1
            }
        }
        count = Math.max(1, Math.min(count, 10));
        return ApiResponse.success(quizService.generateQuiz(topic, count));
    }

    /**
     * 提交答案并自动批改。
     * <p>HTTP: {@code POST /api/quiz/grade}</p>
     * <p>逐题比对用户答案与正确答案，统计正确率；已登录用户答错的题目会自动加入错题本。</p>
     *
     * @param request       答题提交请求，包含题目、答案、课程与知识点信息
     * @param currentUserId 可选的当前登录用户 ID，用于错题本记录
     * @return 批改结果，包含总分、正确数、错误数及每题详情
     */
    @Operation(summary = "提交答案并自动批改")
    @PostMapping("/grade")
    public ApiResponse<QuizGradingResult> gradeQuiz(@RequestBody QuizSubmitRequest request,
                                                     @RequestAttribute(required = false) Integer currentUserId) {
        QuizGradingResult result = new QuizGradingResult();
        List<QuizGradingResult.QuestionResult> results = new ArrayList<>();
        int correctCount = 0;

        // 遍历用户提交的每道题目进行批改
        if (request.getAnswers() != null) {
            for (QuizSubmitRequest.AnswerItem item : request.getAnswers()) {
                QuizGradingResult.QuestionResult qr = new QuizGradingResult.QuestionResult();
                qr.setQuestionNo(item.getQuestionNo());
                qr.setQuestion(item.getQuestion());
                qr.setUserAnswer(item.getUserAnswer());
                qr.setCorrectAnswer(item.getCorrectAnswer());

                // 忽略大小写比较用户答案与正确答案
                boolean correct = item.getCorrectAnswer() != null
                    && item.getCorrectAnswer().equalsIgnoreCase(item.getUserAnswer());
                qr.setCorrect(correct);
                qr.setExplanation(item.getExplanation());

                if (correct) {
                    correctCount++;
                } else if (currentUserId != null) {
                    // 答错的题目自动加入当前用户的错题本
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

        // 汇总批改统计信息
        result.setTotalQuestions(results.size());
        result.setCorrectCount(correctCount);
        result.setWrongCount(results.size() - correctCount);
        result.setAccuracyRate(results.size() > 0 ? Math.round(correctCount * 100f / results.size()) : 0);
        result.setResults(results);

        return ApiResponse.success(result);
    }
}
