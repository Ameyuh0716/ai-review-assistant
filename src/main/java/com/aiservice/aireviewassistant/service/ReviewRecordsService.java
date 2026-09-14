package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.dto.StudyProgressDto;
import com.aiservice.aireviewassistant.dto.UserProgressDto;
import com.aiservice.aireviewassistant.entity.ReviewRecords;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

// 复习记录表 服务接口
public interface ReviewRecordsService extends IService<ReviewRecords> {

    // 保存一次Agent对话产生的复习记录，自动根据会话查询课程ID
    boolean saveFromAgent(Integer conversationId, String userId, String question, String answer);

    // 根据课程查询复习记录
    List<ReviewRecords> listByCourse(Integer courseId);

    // 根据用户查询复习记录
    List<ReviewRecords> listByUser(String userId);

    // 查询某用户某课程的学习进度
    StudyProgressDto getStudyProgress(Integer courseId, String userId);

    // 查询某用户的整体学习进度总览
    UserProgressDto getUserOverallProgress(String userId);
}
