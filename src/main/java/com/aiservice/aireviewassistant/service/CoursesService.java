package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.Courses;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 课程服务接口。
 * <p>负责课程数据的查询，按用户维度隔离课程列表。</p>
 */
public interface CoursesService extends IService<Courses> {

    /**
     * 查询指定用户的所有课程。
     * <p>返回该用户创建或关联的课程列表，通常按最近更新时间倒序排列。</p>
     *
     * @param userId 用户主键 ID
     * @return 该用户的课程列表；无记录时返回空列表
     */
    List<Courses> listByUserId(Integer userId);
}
