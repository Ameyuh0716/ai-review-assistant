package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 错题本表：收集用户答错的题目，支持错题回顾和重练
@Getter
@Setter
@TableName("wrong_answer_book")
public class WrongAnswerBook {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("user_id")
    private Integer userId;

    @TableField("course_id")
    private Integer courseId;

    @TableField("question")
    private String question;

    @TableField("options")
    private String options;

    @TableField("correct_answer")
    private String correctAnswer;

    @TableField("user_answer")
    private String userAnswer;

    @TableField("explanation")
    private String explanation;

    @TableField("topic")
    private String topic;

    @TableField("is_mastered")
    private Boolean isMastered;

    @TableField("wrong_count")
    private Integer wrongCount;

    @TableField("last_wrong_at")
    private LocalDateTime lastWrongAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
