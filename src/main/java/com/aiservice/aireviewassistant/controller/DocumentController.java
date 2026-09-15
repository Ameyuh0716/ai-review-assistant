package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.service.DocumentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档控制器。
 * <p>负责课程相关文档的上传与文本导入，上传后的文档会经过解析并写入向量知识库，
 * 用于后续 RAG 检索与问答。</p>
 */
@RestController
@RequestMapping("/api/document")
@Validated
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 构造方法，注入文档服务。
     *
     * @param documentService 文档处理服务，负责解析与向量化
     */
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传文档并向量化。
     * <p>HTTP: {@code POST /api/document/upload?courseId=1}</p>
     * <p>支持上传文件后自动解析文本、切分并写入课程对应的知识库。</p>
     *
     * @param courseId 课程 ID，不能为空
     * @param file     上传的文件
     * @return 处理结果提示文本
     */
    @PostMapping("/upload")
    public String uploadDocument(
            @RequestParam("courseId") @NotNull(message = "课程ID不能为空") Integer courseId,
            @RequestParam("file") MultipartFile file) {
        // 前置校验：文件为空时直接返回错误提示，避免进入后续处理
        if (file == null || file.isEmpty()) {
            return "错误：请选择要上传的文件";
        }
        return documentService.uploadAndVectorize(courseId, file);
    }

    /**
     * 直接导入文本内容。
     * <p>HTTP: {@code POST /api/document/import?courseId=1}</p>
     * <p>适用于前端直接粘贴文本或测试场景，导入后同样会进行向量化处理。</p>
     *
     * @param courseId 课程 ID，不能为空
     * @param content  待导入的文本内容，不能为空
     * @return 处理结果提示文本
     */
    @PostMapping("/import")
    public String importContent(
            @RequestParam("courseId") @NotNull(message = "课程ID不能为空") Integer courseId,
            @RequestBody @NotBlank(message = "内容不能为空") String content) {
        return documentService.importContent(courseId, content);
    }
}
