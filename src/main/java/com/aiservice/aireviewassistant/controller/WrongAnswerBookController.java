package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.WrongAnswerBook;
import com.aiservice.aireviewassistant.service.WrongAnswerBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 错题本控制器
@Tag(name = "错题本", description = "查看、标记掌握、删除错题")
@RestController
@RequestMapping("/api/wrong-book")
public class WrongAnswerBookController {

    private final WrongAnswerBookService wrongAnswerBookService;

    public WrongAnswerBookController(WrongAnswerBookService wrongAnswerBookService) {
        this.wrongAnswerBookService = wrongAnswerBookService;
    }

    // 查询错题列表
    @Operation(summary = "查询错题列表")
    @GetMapping
    public ApiResponse<List<WrongAnswerBook>> list(
            @RequestAttribute(required = false) Integer currentUserId,
            @RequestParam(required = false) Integer courseId,
            @RequestParam(required = false) Boolean mastered) {
        Integer userId = currentUserId != null ? currentUserId : 0;
        return ApiResponse.success(wrongAnswerBookService.listWrong(userId, courseId, mastered));
    }

    // 标记错题已掌握
    @Operation(summary = "标记错题已掌握")
    @PutMapping("/{id}/master")
    public ApiResponse<Boolean> markMastered(@PathVariable Integer id) {
        return ApiResponse.success(wrongAnswerBookService.markMastered(id));
    }

    // 删除错题
    @Operation(summary = "删除错题")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Integer id) {
        return ApiResponse.success(wrongAnswerBookService.removeWrong(id));
    }

    // 获取错题统计
    @Operation(summary = "获取错题统计")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(
            @RequestAttribute(required = false) Integer currentUserId) {
        Integer userId = currentUserId != null ? currentUserId : 0;
        return ApiResponse.success(wrongAnswerBookService.getStats(userId));
    }
}
