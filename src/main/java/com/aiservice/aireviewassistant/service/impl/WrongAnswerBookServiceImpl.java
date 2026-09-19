package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.WrongAnswerBook;
import com.aiservice.aireviewassistant.mapper.WrongAnswerBookMapper;
import com.aiservice.aireviewassistant.service.WrongAnswerBookService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 错题本服务实现类。
 * <p>
 * 实现 {@link WrongAnswerBookService} 接口，提供错题记录、查询、掌握标记、删除及统计功能。
 * 记录错题时会对同题未掌握的记录进行合并，仅增加错误次数，避免重复题目堆积。
 * </p>
 */
@Service
public class WrongAnswerBookServiceImpl extends ServiceImpl<WrongAnswerBookMapper, WrongAnswerBook>
        implements WrongAnswerBookService {

    /**
     * 记录一道错题。
     * <p>
     * 先去重查询该用户是否存在相同题目且未掌握的记录：
     * <ul>
     *   <li>存在：错误次数 +1，更新用户答案与最后错误时间；</li>
     *   <li>不存在：新增一条错题记录，初始错误次数为 1。</li>
     * </ul>
     * </p>
     *
     * @param userId        用户 ID
     * @param courseId      课程 ID
     * @param question      题目内容
     * @param options       选项内容
     * @param correctAnswer 正确答案
     * @param userAnswer    用户答案
     * @param explanation   答案解析
     * @param topic         知识点/主题
     * @return 是否记录成功
     */
    @Override
    public boolean recordWrong(Integer userId, Integer courseId, String question,
                               String options, String correctAnswer, String userAnswer,
                               String explanation, String topic) {
        // 查找该用户是否已有相同题目且未掌握的记录，避免重复录入
        WrongAnswerBook existing = lambdaQuery()
            .eq(WrongAnswerBook::getUserId, userId)
            .eq(WrongAnswerBook::getQuestion, question)
            .eq(WrongAnswerBook::getIsMastered, false)
            .one();

        if (existing != null) {
            // 已存在未掌握记录，累加错误次数并更新最新作答信息
            existing.setWrongCount(existing.getWrongCount() + 1);
            existing.setUserAnswer(userAnswer);
            existing.setLastWrongAt(LocalDateTime.now());
            return updateById(existing);
        }

        // 不存在则新增错题记录
        WrongAnswerBook book = new WrongAnswerBook();
        book.setUserId(userId);
        book.setCourseId(courseId);
        book.setQuestion(question);
        book.setOptions(options);
        book.setCorrectAnswer(correctAnswer);
        book.setUserAnswer(userAnswer);
        book.setExplanation(explanation);
        book.setTopic(topic);
        book.setIsMastered(false);
        book.setWrongCount(1);
        book.setLastWrongAt(LocalDateTime.now());
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());
        return save(book);
    }

    /**
     * 查询用户错题列表，支持按课程与掌握状态动态过滤，按最后错误时间倒序。
     *
     * @param userId   用户 ID
     * @param courseId 课程 ID，可选
     * @param mastered 掌握状态，可选
     * @return 错题列表
     */
    @Override
    public List<WrongAnswerBook> listWrong(Integer userId, Integer courseId, Boolean mastered) {
        return listWrong(userId, courseId, mastered, null, null);
    }

    /**
     * 查询用户错题列表（扩展筛选：知识点 + 关键词）。
     *
     * @param userId   用户 ID
     * @param courseId 课程 ID，可选
     * @param mastered 掌握状态，可选
     * @param topic    知识点精确过滤，可选
     * @param keyword  关键词模糊搜索（题干/知识点/解析），可选
     * @return 错题列表
     */
    @Override
    public List<WrongAnswerBook> listWrong(Integer userId, Integer courseId, Boolean mastered,
                                           String topic, String keyword) {
        var query = lambdaQuery()
            .eq(WrongAnswerBook::getUserId, userId);
        // 动态追加课程过滤条件
        if (courseId != null) {
            query.eq(WrongAnswerBook::getCourseId, courseId);
        }
        // 动态追加掌握状态过滤条件
        if (mastered != null) {
            query.eq(WrongAnswerBook::getIsMastered, mastered);
        }
        // 知识点精确过滤
        if (topic != null && !topic.isBlank()) {
            query.eq(WrongAnswerBook::getTopic, topic.trim());
        }
        // 关键词模糊搜索：题干 / 知识点 / 解析 任一命中即可
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            query.and(w -> w.like(WrongAnswerBook::getQuestion, kw)
                .or().like(WrongAnswerBook::getTopic, kw)
                .or().like(WrongAnswerBook::getExplanation, kw));
        }
        return query.orderByDesc(WrongAnswerBook::getLastWrongAt).list();
    }

    /**
     * 重做一道错题并校验答案。
     * <p>答对自动标记掌握；答错则错误次数 +1 并更新最后错误时间。</p>
     *
     * @param id         错题记录 ID
     * @param userAnswer 用户本次选择的答案
     * @return 重做结果；错题不存在时返回 null
     */
    @Override
    public Map<String, Object> redo(Integer id, String userAnswer) {
        WrongAnswerBook book = getById(id);
        if (book == null) {
            return null;
        }
        String correctAnswer = book.getCorrectAnswer() == null ? "" : book.getCorrectAnswer().trim();
        String answer = userAnswer == null ? "" : userAnswer.trim();
        // 兼容正确答案为选项字母或完整选项文本两种存储形式
        boolean correct = !correctAnswer.isEmpty() && !answer.isEmpty()
            && (correctAnswer.equalsIgnoreCase(answer)
                || correctAnswer.toUpperCase().startsWith(answer.toUpperCase()));

        if (correct) {
            // 答对：标记为已掌握
            book.setIsMastered(true);
            book.setUserAnswer(answer);
            book.setUpdatedAt(LocalDateTime.now());
            updateById(book);
        } else {
            // 答错：错误次数 +1，回到待复习状态（此前即使标记过掌握也应重新纳入复习）
            book.setWrongCount((book.getWrongCount() == null ? 0 : book.getWrongCount()) + 1);
            book.setUserAnswer(answer);
            book.setIsMastered(false);
            book.setLastWrongAt(LocalDateTime.now());
            book.setUpdatedAt(LocalDateTime.now());
            updateById(book);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("correct", correct);
        result.put("correctAnswer", book.getCorrectAnswer());
        result.put("explanation", book.getExplanation());
        result.put("wrongCount", book.getWrongCount());
        result.put("isMastered", book.getIsMastered());
        return result;
    }

    /**
     * 将指定 ID 的错题标记为已掌握。
     *
     * @param id 错题记录 ID
     * @return 是否标记成功；记录不存在时返回 false
     */
    @Override
    public boolean markMastered(Integer id) {
        WrongAnswerBook book = getById(id);
        if (book == null) return false;
        book.setIsMastered(true);
        book.setUpdatedAt(LocalDateTime.now());
        return updateById(book);
    }

    /**
     * 删除指定 ID 的错题。
     *
     * @param id 错题记录 ID
     * @return 是否删除成功
     */
    @Override
    public boolean removeWrong(Integer id) {
        return removeById(id);
    }

    /**
     * 获取用户错题统计。
     * <p>
     * 统计结果包含 total（总数）、mastered（已掌握数）、unmastered（未掌握数）、
     * masteryRate（掌握率，四舍五入到整数百分比）。
     * </p>
     *
     * @param userId 用户 ID
     * @return 错题统计映射
     */
    @Override
    public Map<String, Object> getStats(Integer userId) {
        long total = lambdaQuery().eq(WrongAnswerBook::getUserId, userId).count();
        long mastered = lambdaQuery().eq(WrongAnswerBook::getUserId, userId)
            .eq(WrongAnswerBook::getIsMastered, true).count();
        long unmastered = total - mastered;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("mastered", mastered);
        stats.put("unmastered", unmastered);
        // 无错题时掌握率为 0，避免除零
        stats.put("masteryRate", total > 0 ? Math.round(mastered * 100.0 / total) : 0);
        return stats;
    }
}
