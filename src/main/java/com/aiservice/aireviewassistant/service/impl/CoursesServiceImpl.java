package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.mapper.CoursesMapper;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

// 课程表 服务实现类
@Service
public class CoursesServiceImpl extends ServiceImpl<CoursesMapper, Courses> implements CoursesService {

    @Override
    public List<Courses> listByUserId(Integer userId) {
        return lambdaQuery()
            .eq(Courses::getUserId, userId)
            .orderByDesc(Courses::getUpdatedAt)
            .list();
    }
}
