package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.WrongAnswerBook;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 错题本表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code wrong_answer_book}，实体类型为 {@link WrongAnswerBook}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于收集用户在测验或练习中答错的题目、答案、解析及掌握状态。
 * </p>
 */
public interface WrongAnswerBookMapper extends BaseMapper<WrongAnswerBook> {
}
