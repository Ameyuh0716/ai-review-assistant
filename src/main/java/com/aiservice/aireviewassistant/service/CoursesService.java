package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.Courses;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

// 课程表 服务接口
public interface CoursesService extends IService<Courses> {

    // 查询指定用户的所有课程
    List<Courses> listByUserId(Integer userId);
}
