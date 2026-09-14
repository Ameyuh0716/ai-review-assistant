package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.WrongAnswerBook;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

// 错题本服务接口
public interface WrongAnswerBookService extends IService<WrongAnswerBook> {

    // 记录一道错题
    boolean recordWrong(Integer userId, Integer courseId, String question,
                        String options, String correctAnswer, String userAnswer,
                        String explanation, String topic);

    // 查询用户错题列表（支持按课程、掌握状态过滤）
    List<WrongAnswerBook> listWrong(Integer userId, Integer courseId, Boolean mastered);

    // 标记错题已掌握
    boolean markMastered(Integer id);

    // 删除错题
    boolean removeWrong(Integer id);

    // 获取错题统计
    Map<String, Object> getStats(Integer userId);
}
