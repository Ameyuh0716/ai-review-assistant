package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库原始资料表。
 * <p>
 * 保存上传文件的解析后全文，用于知识库管理页"查看完整源文件预览"。
 * 向量库（vector_store）保存的是分块，此处保存完整原文，二者通过
 * {@code course_id + file_name} 关联。
 * </p>
 */
@Getter
@Setter
@TableName("knowledge_document")
public class KnowledgeDocument {

    /** 资料主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 所属课程 ID，对应 {@link Courses#id}。 */
    @TableField("course_id")
    private Integer courseId;

    /** 资料名称（上传文件名或手动导入时的命名）。 */
    @TableField("file_name")
    private String fileName;

    /** 解析后的完整文本内容。 */
    @TableField("content")
    private String content;

    /** 分块数量（冗余字段，便于列表展示）。 */
    @TableField("chunk_count")
    private Integer chunkCount;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
