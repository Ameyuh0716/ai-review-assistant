package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.dto.StudyProgressDto;
import com.aiservice.aireviewassistant.dto.UserProgressDto;
import com.aiservice.aireviewassistant.entity.ReviewRecords;
import com.aiservice.aireviewassistant.service.ReviewRecordsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 复习记录表 前端控制器：提供复习记录 CRUD 与 学习进度统计 接口
@Tag(name = "复习记录与学习进度", description = "查询复习记录、课程进度、用户总览")
@RestController
@RequestMapping("/api/review-records")
public class ReviewRecordsController {

    private final ReviewRecordsService reviewRecordsService;

    public ReviewRecordsController(ReviewRecordsService reviewRecordsService) {
        this.reviewRecordsService = reviewRecordsService;
    }

    // 查询所有复习记录
    @GetMapping
    public List<ReviewRecords> list() {
        return reviewRecordsService.list();
    }

    // 根据ID查询复习记录
    @GetMapping("/{id}")
    public ReviewRecords getById(@PathVariable Integer id) {
        return reviewRecordsService.getById(id);
    }

    // 根据课程查询复习记录
    @Operation(summary = "按课程查询复习记录")
    @GetMapping("/course/{courseId}")
    public List<ReviewRecords> listByCourse(@PathVariable Integer courseId) {
        return reviewRecordsService.listByCourse(courseId);
    }

    // 根据用户查询复习记录
    @Operation(summary = "按用户查询复习记录")
    @GetMapping("/user/{userId}")
    public List<ReviewRecords> listByUser(@PathVariable String userId) {
        return reviewRecordsService.listByUser(userId);
    }

    // 查询某用户某课程的学习进度
    @Operation(summary = "查询课程学习进度")
    @GetMapping("/progress/{courseId}")
    public StudyProgressDto getStudyProgress(@PathVariable Integer courseId,
                                             @RequestParam String userId) {
        return reviewRecordsService.getStudyProgress(courseId, userId);
    }

    // 查询用户整体学习进度总览
    @Operation(summary = "查询用户整体学习进度")
    @GetMapping("/progress/user/{userId}")
    public UserProgressDto getUserOverallProgress(@PathVariable String userId) {
        return reviewRecordsService.getUserOverallProgress(userId);
    }

    // 新增复习记录
    @PostMapping
    public boolean save(@RequestBody ReviewRecords record) {
        return reviewRecordsService.save(record);
    }

    // 更新复习记录
    @PutMapping("/{id}")
    public boolean update(@PathVariable Integer id, @RequestBody ReviewRecords record) {
        record.setId(id);
        return reviewRecordsService.updateById(record);
    }

    // 删除复习记录
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return reviewRecordsService.removeById(id);
    }
}
