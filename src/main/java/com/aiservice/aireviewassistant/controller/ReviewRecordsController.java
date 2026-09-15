package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.dto.StudyProgressDto;
import com.aiservice.aireviewassistant.dto.UserProgressDto;
import com.aiservice.aireviewassistant.entity.ReviewRecords;
import com.aiservice.aireviewassistant.service.ReviewRecordsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 复习记录与学习进度控制器。
 * <p>提供复习记录的增删改查以及课程进度、用户整体进度统计接口，基础路径为 /api/review-records。</p>
 */
@Tag(name = "复习记录与学习进度", description = "查询复习记录、课程进度、用户总览")
@RestController
@RequestMapping("/api/review-records")
public class ReviewRecordsController {

    private final ReviewRecordsService reviewRecordsService;

    /**
     * 通过依赖注入构造控制器。
     *
     * @param reviewRecordsService 复习记录服务
     */
    public ReviewRecordsController(ReviewRecordsService reviewRecordsService) {
        this.reviewRecordsService = reviewRecordsService;
    }

    /**
     * 查询所有复习记录。
     * <p>GET /api/review-records</p>
     *
     * @return 复习记录列表
     */
    @GetMapping
    public List<ReviewRecords> list() {
        return reviewRecordsService.list();
    }

    /**
     * 根据 ID 查询复习记录。
     * <p>GET /api/review-records/{id}</p>
     *
     * @param id 复习记录主键
     * @return 复习记录实体
     */
    @GetMapping("/{id}")
    public ReviewRecords getById(@PathVariable Integer id) {
        return reviewRecordsService.getById(id);
    }

    /**
     * 按课程查询复习记录。
     * <p>GET /api/review-records/course/{courseId}</p>
     *
     * @param courseId 课程 ID
     * @return 该课程下的复习记录列表
     */
    @Operation(summary = "按课程查询复习记录")
    @GetMapping("/course/{courseId}")
    public List<ReviewRecords> listByCourse(@PathVariable Integer courseId) {
        return reviewRecordsService.listByCourse(courseId);
    }

    /**
     * 按用户查询复习记录。
     * <p>GET /api/review-records/user/{userId}</p>
     *
     * @param userId 用户 ID
     * @return 该用户的复习记录列表
     */
    @Operation(summary = "按用户查询复习记录")
    @GetMapping("/user/{userId}")
    public List<ReviewRecords> listByUser(@PathVariable String userId) {
        return reviewRecordsService.listByUser(userId);
    }

    /**
     * 查询某用户某课程的学习进度。
     * <p>GET /api/review-records/progress/{courseId}</p>
     *
     * @param courseId 课程 ID
     * @param userId 用户 ID
     * @return 课程学习进度 DTO
     */
    @Operation(summary = "查询课程学习进度")
    @GetMapping("/progress/{courseId}")
    public StudyProgressDto getStudyProgress(@PathVariable Integer courseId,
                                             @RequestParam String userId) {
        return reviewRecordsService.getStudyProgress(courseId, userId);
    }

    /**
     * 查询用户整体学习进度总览。
     * <p>GET /api/review-records/progress/user/{userId}</p>
     *
     * @param userId 用户 ID
     * @return 用户整体学习进度 DTO
     */
    @Operation(summary = "查询用户整体学习进度")
    @GetMapping("/progress/user/{userId}")
    public UserProgressDto getUserOverallProgress(@PathVariable String userId) {
        return reviewRecordsService.getUserOverallProgress(userId);
    }

    /**
     * 新增复习记录。
     * <p>POST /api/review-records</p>
     *
     * @param record 复习记录实体
     * @return 保存是否成功
     */
    @PostMapping
    public boolean save(@RequestBody ReviewRecords record) {
        return reviewRecordsService.save(record);
    }

    /**
     * 更新复习记录。
     * <p>PUT /api/review-records/{id}</p>
     *
     * @param id 复习记录主键
     * @param record 复习记录实体
     * @return 更新是否成功
     */
    @PutMapping("/{id}")
    public boolean update(@PathVariable Integer id, @RequestBody ReviewRecords record) {
        // 将路径变量 id 绑定到实体主键，确保更新目标正确
        record.setId(id);
        return reviewRecordsService.updateById(record);
    }

    /**
     * 删除复习记录。
     * <p>DELETE /api/review-records/{id}</p>
     *
     * @param id 复习记录主键
     * @return 删除是否成功
     */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        return reviewRecordsService.removeById(id);
    }
}
