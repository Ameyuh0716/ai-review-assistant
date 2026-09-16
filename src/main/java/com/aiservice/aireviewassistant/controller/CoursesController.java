package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.service.CoursesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程管理控制器。
 * <p>提供课程表的增删改查接口，数据按当前登录用户隔离，未登录用户默认归属到用户 0。</p>
 */
@Tag(name = "课程管理", description = "课程的增删改查，数据按用户隔离")
@RestController
@RequestMapping("/api/courses")
public class CoursesController {

    private final CoursesService coursesService;

    /**
     * 构造方法，注入课程服务。
     *
     * @param coursesService 课程 CRUD 服务
     */
    public CoursesController(CoursesService coursesService) {
        this.coursesService = coursesService;
    }

    /**
     * 查询当前用户的所有课程。
     * <p>HTTP: {@code GET /courses}</p>
     * <p>未登录用户默认查询 userId=0 的课程。</p>
     *
     * @param currentUserId 可选的当前登录用户 ID
     * @return 当前用户课程列表的通用响应
     */
    @Operation(summary = "查询我的课程列表")
    @GetMapping
    public ApiResponse<List<Courses>> list(@RequestAttribute(required = false) Integer currentUserId) {
        // 将可能为空的用户 ID 转换为 0，保证未登录用户也能查看默认课程
        Integer userId = currentUserId != null ? currentUserId : 0;
        return ApiResponse.success(coursesService.listByUserId(userId));
    }

    /**
     * 根据 ID 查询单个课程。
     * <p>HTTP: {@code GET /courses/{id}}</p>
     * <p>课程不存在时返回 404 错误响应。</p>
     *
     * @param id 课程 ID
     * @return 课程对象的通用响应，或 404 错误响应
     */
    @Operation(summary = "根据ID查询课程")
    @GetMapping("/{id}")
    public ApiResponse<Courses> getById(@PathVariable Integer id) {
        Courses course = coursesService.getById(id);
        if (course == null) {
            return ApiResponse.error(404, "课程不存在");
        }
        return ApiResponse.success(course);
    }

    /**
     * 新增课程。
     * <p>HTTP: {@code POST /courses}</p>
     * <p>自动将课程关联到当前登录用户，未登录用户归属到用户 0。</p>
     *
     * @param course        待保存的课程对象
     * @param currentUserId 可选的当前登录用户 ID
     * @return 保存后的课程对象的通用响应
     */
    @Operation(summary = "新增课程")
    @PostMapping
    public ApiResponse<Courses> save(@RequestBody Courses course,
                                     @RequestAttribute(required = false) Integer currentUserId) {
        Integer userId = currentUserId != null ? currentUserId : 0;
        course.setUserId(userId);
        coursesService.save(course);
        return ApiResponse.success(course);
    }

    /**
     * 更新课程信息。
     * <p>HTTP: {@code PUT /courses/{id}}</p>
     *
     * @param id     课程 ID
     * @param course 包含更新字段的课程对象
     * @return 更新后的课程对象的通用响应
     */
    @Operation(summary = "更新课程")
    @PutMapping("/{id}")
    public ApiResponse<Courses> update(@PathVariable Integer id, @RequestBody Courses course) {
        course.setId(id);
        coursesService.updateById(course);
        return ApiResponse.success(course);
    }

    /**
     * 删除课程。
     * <p>HTTP: {@code DELETE /courses/{id}}</p>
     *
     * @param id 课程 ID
     * @return 删除结果的通用响应
     */
    @Operation(summary = "删除课程")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Integer id) {
        boolean result = coursesService.removeById(id);
        return ApiResponse.success(result);
    }
}
