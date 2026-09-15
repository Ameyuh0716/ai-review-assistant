package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 错题本表。
 * <p>
 * 收集用户在测验或练习中答错的题目，记录题目、选项、正确答案、用户答案、解析及掌握状态，
 * 支持错题回顾、重练与掌握度统计。
 * </p>
 */
@Getter
@Setter
@TableName("wrong_answer_book")
public class WrongAnswerBook {

    /** 错题主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 所属用户 ID，对应 {@link AppUser#id}。 */
    @TableField("user_id")
    private Integer userId;

    /** 关联课程 ID，对应 {@link Courses#id}。 */
    @TableField("course_id")
    private Integer courseId;

    /** 题目内容。 */
    @TableField("question")
    private String question;

    /** 题目选项，以 JSON 字符串形式存储，例如 {"A":"...","B":"..."}。 */
    @TableField("options")
    private String options;

    /** 正确答案，如 A、B、C、D 或具体文本。 */
    @TableField("correct_answer")
    private String correctAnswer;

    /** 用户实际选择的答案。 */
    @TableField("user_answer")
    private String userAnswer;

    /** 答案解析，说明正确选项原因及知识点。 */
    @TableField("explanation")
    private String explanation;

    /** 知识点/主题标签，用于分类与针对性复习。 */
    @TableField("topic")
    private String topic;

    /** 是否已掌握：true 已掌握，false 未掌握。 */
    @TableField("is_mastered")
    private Boolean isMastered;

    /** 答错次数，用于统计易错程度。 */
    @TableField("wrong_count")
    private Integer wrongCount;

    /** 最近一次答错时间。 */
    @TableField("last_wrong_at")
    private LocalDateTime lastWrongAt;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 记录最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
