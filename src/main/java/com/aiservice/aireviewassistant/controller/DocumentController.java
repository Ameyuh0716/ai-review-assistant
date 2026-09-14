package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.service.DocumentService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
@Validated
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    // 上传文档并向量化：POST /api/document/upload?courseId=1
    @PostMapping("/upload")
    public String uploadDocument(
            @RequestParam("courseId") @NotNull(message = "课程ID不能为空") Integer courseId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "错误：请选择要上传的文件";
        }
        return documentService.uploadAndVectorize(courseId, file);
    }

    // 直接导入文本内容：POST /api/document/import?courseId=1
    @PostMapping("/import")
    public String importContent(
            @RequestParam("courseId") @NotNull(message = "课程ID不能为空") Integer courseId,
            @RequestBody @NotBlank(message = "内容不能为空") String content) {
        return documentService.importContent(courseId, content);
    }
}
