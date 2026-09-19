package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.WrongAnswerBook;
import com.aiservice.aireviewassistant.service.WrongAnswerBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 错题本控制器。
 * <p>提供错题查看、标记掌握、删除与统计接口，基础路径为 /api/wrong-book。</p>
 */
@Tag(name = "错题本", description = "查看、标记掌握、删除错题")
@RestController
@RequestMapping("/api/wrong-book")
public class WrongAnswerBookController {

    private final WrongAnswerBookService wrongAnswerBookService;

    /**
     * 通过依赖注入构造控制器。
     *
     * @param wrongAnswerBookService 错题本服务
     */
    public WrongAnswerBookController(WrongAnswerBookService wrongAnswerBookService) {
        this.wrongAnswerBookService = wrongAnswerBookService;
    }

    /**
     * 查询错题列表。
     * <p>GET /api/wrong-book，支持按课程、掌握状态、知识点与关键词过滤。</p>
     *
     * @param currentUserId 当前用户 ID（可选）
     * @param courseId 课程 ID（可选）
     * @param mastered 是否已掌握（可选）
     * @param topic 知识点/主题（可选，精确匹配）
     * @param keyword 关键词（可选，模糊匹配题干/知识点/解析）
     * @return 错题列表
     */
    @Operation(summary = "查询错题列表")
    @GetMapping
    public ApiResponse<List<WrongAnswerBook>> list(
            @RequestAttribute(required = false) Integer currentUserId,
            @RequestParam(required = false) Integer courseId,
            @RequestParam(required = false) Boolean mastered,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String keyword) {
        // 未登录用户统一使用 0 作为匿名标识
        Integer userId = currentUserId != null ? currentUserId : 0;
        return ApiResponse.success(wrongAnswerBookService.listWrong(userId, courseId, mastered, topic, keyword));
    }

    /**
     * 重做一道错题并提交答案。
     * <p>POST /api/wrong-book/{id}/redo，请求体：{@code {"answer": "A"}}。</p>
     * <p>答对自动标记掌握；答错则错误次数 +1。</p>
     *
     * @param id   错题主键
     * @param body 包含 answer 的请求体
     * @return 重做结果；错题不存在时返回 404
     */
    @Operation(summary = "重做错题")
    @PostMapping("/{id}/redo")
    public ApiResponse<Map<String, Object>> redo(@PathVariable Integer id,
                                                 @RequestBody Map<String, Object> body) {
        Object answerObj = body.get("answer");
        Map<String, Object> result = wrongAnswerBookService.redo(
            id, answerObj != null ? String.valueOf(answerObj) : "");
        if (result == null) {
            return ApiResponse.error(404, "错题不存在");
        }
        return ApiResponse.success(result);
    }

    /**
     * 标记错题已掌握。
     * <p>PUT /api/wrong-book/{id}/master</p>
     *
     * @param id 错题主键
     * @return 是否标记成功
     */
    @Operation(summary = "标记错题已掌握")
    @PutMapping("/{id}/master")
    public ApiResponse<Boolean> markMastered(@PathVariable Integer id) {
        return ApiResponse.success(wrongAnswerBookService.markMastered(id));
    }

    /**
     * 删除错题。
     * <p>DELETE /api/wrong-book/{id}</p>
     *
     * @param id 错题主键
     * @return 是否删除成功
     */
    @Operation(summary = "删除错题")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Integer id) {
        return ApiResponse.success(wrongAnswerBookService.removeWrong(id));
    }

    /**
     * 获取错题统计。
     * <p>GET /api/wrong-book/stats</p>
     *
     * @param currentUserId 当前用户 ID（可选）
     * @return 错题统计数据
     */
    @Operation(summary = "获取错题统计")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestAttribute(required = false) Integer currentUserId) {
        // 未登录用户统一使用 0 作为匿名标识
        Integer userId = currentUserId != null ? currentUserId : 0;
        return ApiResponse.success(wrongAnswerBookService.getStats(userId));
    }
}
