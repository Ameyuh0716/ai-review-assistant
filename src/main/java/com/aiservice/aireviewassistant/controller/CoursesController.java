package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.service.CoursesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 课程表 前端控制器：提供课程 CRUD 接口（按用户隔离）
@Tag(name = "课程管理", description = "课程的增删改查，数据按用户隔离")
@RestController
@RequestMapping("/courses")
public class CoursesController {

    private final CoursesService coursesService;

    public CoursesController(CoursesService coursesService) {
        this.coursesService = coursesService;
    }

    // 查询当前用户的所有课程
    @Operation(summary = "查询我的课程列表")
    @GetMapping
    public ApiResponse<List<Courses>> list(@RequestAttribute(required = false) Integer currentUserId) {
        Integer userId = currentUserId != null ? currentUserId : 0;
        return ApiResponse.success(coursesService.listByUserId(userId));
    }

    // 根据ID查询课程
    @Operation(summary = "根据ID查询课程")
    @GetMapping("/{id}")
    public ApiResponse<Courses> getById(@PathVariable Integer id) {
        Courses course = coursesService.getById(id);
        if (course == null) {
            return ApiResponse.error(404, "课程不存在");
        }
        return ApiResponse.success(course);
    }

    // 新增课程（自动关联当前用户）
    @Operation(summary = "新增课程")
    @PostMapping
    public ApiResponse<Courses> save(@RequestBody Courses course,
                                     @RequestAttribute(required = false) Integer currentUserId) {
        Integer userId = currentUserId != null ? currentUserId : 0;
        course.setUserId(userId);
        coursesService.save(course);
        return ApiResponse.success(course);
    }

    // 更新课程
    @Operation(summary = "更新课程")
    @PutMapping("/{id}")
    public ApiResponse<Courses> update(@PathVariable Integer id, @RequestBody Courses course) {
        course.setId(id);
        coursesService.updateById(course);
        return ApiResponse.success(course);
    }

    // 删除课程
    @Operation(summary = "删除课程")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Integer id) {
        boolean result = coursesService.removeById(id);
        return ApiResponse.success(result);
    }
}
