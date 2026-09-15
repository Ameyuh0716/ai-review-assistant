package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.mapper.CoursesMapper;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程服务实现类。
 * <p>基于 MyBatis-Plus 通用 Service 实现按用户 ID 查询课程列表。</p>
 */
@Service
public class CoursesServiceImpl extends ServiceImpl<CoursesMapper, Courses> implements CoursesService {

    @Override
    public List<Courses> listByUserId(Integer userId) {
        // 按用户 ID 等值过滤，并按 updated_at 降序排列，最近更新的课程排在前面
        return lambdaQuery()
            .eq(Courses::getUserId, userId)
            .orderByDesc(Courses::getUpdatedAt)
            .list();
    }
}
