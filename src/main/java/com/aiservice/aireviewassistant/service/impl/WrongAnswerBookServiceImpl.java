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

// 错题本服务实现
@Service
public class WrongAnswerBookServiceImpl extends ServiceImpl<WrongAnswerBookMapper, WrongAnswerBook>
        implements WrongAnswerBookService {

    @Override
    public boolean recordWrong(Integer userId, Integer courseId, String question,
                               String options, String correctAnswer, String userAnswer,
                               String explanation, String topic) {
        // 检查是否已有相同题目
        WrongAnswerBook existing = lambdaQuery()
            .eq(WrongAnswerBook::getUserId, userId)
            .eq(WrongAnswerBook::getQuestion, question)
            .eq(WrongAnswerBook::getIsMastered, false)
            .one();

        if (existing != null) {
            // 已有未掌握的同题，增加错误次数
            existing.setWrongCount(existing.getWrongCount() + 1);
            existing.setUserAnswer(userAnswer);
            existing.setLastWrongAt(LocalDateTime.now());
            return updateById(existing);
        }

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

    @Override
    public List<WrongAnswerBook> listWrong(Integer userId, Integer courseId, Boolean mastered) {
        var query = lambdaQuery()
            .eq(WrongAnswerBook::getUserId, userId);
        if (courseId != null) {
            query.eq(WrongAnswerBook::getCourseId, courseId);
        }
        if (mastered != null) {
            query.eq(WrongAnswerBook::getIsMastered, mastered);
        }
        return query.orderByDesc(WrongAnswerBook::getLastWrongAt).list();
    }

    @Override
    public boolean markMastered(Integer id) {
        WrongAnswerBook book = getById(id);
        if (book == null) return false;
        book.setIsMastered(true);
        book.setUpdatedAt(LocalDateTime.now());
        return updateById(book);
    }

    @Override
    public boolean removeWrong(Integer id) {
        return removeById(id);
    }

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
        stats.put("masteryRate", total > 0 ? Math.round(mastered * 100.0 / total) : 0);
        return stats;
    }
}
