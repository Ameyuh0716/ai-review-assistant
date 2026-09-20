package com.aiservice.aireviewassistant.security;

import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.service.CoursesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 课程资源访问守卫。
 * <p>
 * <b>为什么需要它：</b>知识库相关接口（上传、导入、查看分块、预览原文、删除）都以
 * {@code courseId} 作为唯一定位参数。如果不校验归属，任何已登录用户只要猜到或遍历
 * courseId，就能读取、篡改甚至删除<b>别人课程</b>的资料——这既是数据泄露也是数据破坏。
 * </p>
 * <p>
 * 同一套隔离还必须在检索侧生效（见 {@code RagService.RagScope}）：
 * 否则即使读取接口守住了，向量检索仍可能把他人资料带进回答里。
 * </p>
 */
@Slf4j
@Component
public class CourseAccessGuard {

    private final CoursesService coursesService;

    /**
     * 构造方法。
     *
     * @param coursesService 课程服务，用于查询课程归属
     */
    public CourseAccessGuard(CoursesService coursesService) {
        this.coursesService = coursesService;
    }

    /**
     * 判断目标课程是否可被当前用户访问。
     * <p>
     * 以下情况一律视为不可访问：课程 ID 为空、用户未登录、课程不存在、课程属于他人。
     * 对"不存在"与"不属于自己"给出同样的否定结论，避免通过响应差异探测他人课程是否存在。
     * </p>
     *
     * @param courseId 目标课程 ID
     * @param userId   当前登录用户 ID，为 null 表示匿名
     * @return 允许访问时返回 true
     */
    public boolean canAccess(Integer courseId, Integer userId) {
        if (courseId == null || userId == null) {
            return false;
        }
        try {
            Courses course = coursesService.getById(courseId);
            if (course == null) {
                log.debug("[Guard] 课程不存在: courseId={}", courseId);
                return false;
            }
            boolean owner = userId.equals(course.getUserId());
            if (!owner) {
                log.warn("[Guard] 越权访问被拒绝: courseId={} owner={} requester={}",
                    courseId, course.getUserId(), userId);
            }
            return owner;
        } catch (Exception e) {
            // 校验过程本身出错时按"拒绝"处理，避免因异常放行
            log.warn("[Guard] 课程归属校验异常: courseId={}, {}", courseId, e.getMessage());
            return false;
        }
    }
}
