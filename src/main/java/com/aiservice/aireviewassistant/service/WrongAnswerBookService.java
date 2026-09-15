package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.WrongAnswerBook;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 错题本服务接口。
 * <p>
 * 负责记录用户答题过程中的错题，支持按课程与掌握状态查询、标记已掌握、删除错题以及统计掌握率。
 * 边界：专注于 {@link WrongAnswerBook} 实体的生命周期管理，不参与 quiz 生成与判卷逻辑。
 * </p>
 */
public interface WrongAnswerBookService extends IService<WrongAnswerBook> {

    /**
     * 记录一道错题。
     * <p>
     * 若该用户已存在相同题目且未掌握，则仅增加错误次数并更新用户答案与最后错误时间；
     * 否则新增一条错题记录。
     * </p>
     *
     * @param userId        用户 ID
     * @param courseId      课程 ID
     * @param question      题目内容
     * @param options       选项内容，可为 JSON 字符串或纯文本
     * @param correctAnswer 正确答案
     * @param userAnswer    用户答案
     * @param explanation   答案解析
     * @param topic         知识点/主题
     * @return 是否记录成功
     */
    boolean recordWrong(Integer userId, Integer courseId, String question,
                        String options, String correctAnswer, String userAnswer,
                        String explanation, String topic);

    /**
     * 查询用户的错题列表。
     * <p>
     * 支持按课程 ID 与掌握状态进行过滤；两个过滤条件均为可选。
     * 结果按最后错误时间倒序排列，最近错的题目排在前面。
     * </p>
     *
     * @param userId   用户 ID
     * @param courseId 课程 ID，为 null 时不按课程过滤
     * @param mastered 掌握状态，为 null 时不按状态过滤
     * @return 错题列表
     */
    List<WrongAnswerBook> listWrong(Integer userId, Integer courseId, Boolean mastered);

    /**
     * 将指定错题标记为已掌握。
     *
     * @param id 错题记录 ID
     * @return 是否标记成功；记录不存在时返回 false
     */
    boolean markMastered(Integer id);

    /**
     * 删除指定错题。
     *
     * @param id 错题记录 ID
     * @return 是否删除成功
     */
    boolean removeWrong(Integer id);

    /**
     * 获取指定用户的错题统计信息。
     * <p>
     * 返回 total（总数）、mastered（已掌握数）、unmastered（未掌握数）、masteryRate（掌握率，百分比）。
     * </p>
     *
     * @param userId 用户 ID
     * @return 错题统计映射
     */
    Map<String, Object> getStats(Integer userId);
}
