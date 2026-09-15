package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话表。
 * <p>
 * 存储多轮对话的会话基本信息，一条会话可包含多条 {@link Message} 记录。
 * </p>
 */
@Getter
@Setter
@TableName("conversation")
public class Conversation {

    /** 会话主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 会话标题，通常由系统自动生成或用户自定义。 */
    @TableField("title")
    private String title;

    /** 所属用户 ID，对应 {@link AppUser#id}。 */
    @TableField("user_id")
    private String userId;

    /** 关联课程 ID，对应 {@link Courses#id}；可为空。 */
    @TableField("course_id")
    private Integer courseId;

    /** 会话创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 会话最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
