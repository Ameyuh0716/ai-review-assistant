package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.dto.KnowledgeChunkDto;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.DocumentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final VectorStore vectorStore;
    private final CoursesService coursesService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DocumentServiceImpl(VectorStore vectorStore,
                               CoursesService coursesService,
                               JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.coursesService = coursesService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String uploadAndVectorize(Integer courseId, MultipartFile file){
        // 1. 检查课程是否存在
        if (coursesService.getById(courseId) == null) {
            return "错误：课程ID不存在";
        }

        // 2. 读取文件内容
        String content;
        try {
            content = readFileContent(file);
        } catch (Exception e) {
            return "错误：读取文件失败 - " + e.getMessage();
        }

        // 3. 调用导入方法
        return importContent(courseId, content);
    }

    @Override
    public String importContent(Integer courseId, String content) {
        // 1. 文档分块
        List<String> chunks = splitDocument(content);

        if (chunks.isEmpty()) {
            return "错误：文档内容为空或无法分块";
        }

        // 2. 批量构建 Document 并写入 PgVectorStore
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("courseId", courseId);
            metadata.put("chunkIndex", i);
            metadata.put("chunkTotal", chunks.size());
            documents.add(new Document(chunk, metadata));
        }

        try {
            vectorStore.add(documents);
            return "成功：文档已分块并向量化，共处理 " + chunks.size() + " 块";
        } catch (Exception e) {
            log.error("文档向量化失败: {}", e.getMessage(), e);
            return "错误：文档向量化失败 - " + e.getMessage();
        }
    }

    /**
     * 文档分块策略：按段落分块，每块约500字符
     */
    private List<String> splitDocument(String content) {
        List<String> chunks = new ArrayList<>();

        // 按双换行符（段落）分割
        String[] paragraphs = content.split("\n\n");

        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            // 去除前后空白
            paragraph = paragraph.trim();

            // 跳过空段落
            if (paragraph.isEmpty()) {
                continue;
            }

            // 如果当前块加上新段落超过500字符，就保存当前块
            if (currentChunk.length() + paragraph.length() > 500) {
                if (currentChunk.length() > 50) {
                    chunks.add(currentChunk.toString());
                }
                currentChunk = new StringBuilder();
            }

            // 添加段落到当前块
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);
        }

        // 添加最后一个块
        if (currentChunk.length() > 50) {
            chunks.add(currentChunk.toString());
        }

        // 限制最多50个块
        if (chunks.size() > 50) {
            chunks = chunks.subList(0, 50);
        }

        return chunks;
    }

    /**
     * 读取文件内容（支持 txt、md、pdf、doc、docx 格式）
     */
    private String readFileContent(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String lowerName = filename.toLowerCase();

        if (lowerName.endsWith(".pdf")) {
            return readPdfContent(file);
        }
        if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) {
            return readWordContent(file);
        }
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
            return readTextContent(file);
        }
        throw new IllegalArgumentException("仅支持 txt、md、pdf、doc、docx 格式文件");
    }

    // 读取纯文本内容
    private String readTextContent(MultipartFile file) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    // 读取 PDF 文件内容
    private String readPdfContent(MultipartFile file) throws Exception {
        org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes());
        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
        String text = stripper.getText(document);
        document.close();
        return text;
    }

    // 读取 Word 文件内容
    private String readWordContent(MultipartFile file) throws Exception {
        try (java.io.InputStream is = file.getInputStream()) {
            org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(is);
            StringBuilder content = new StringBuilder();
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : document.getParagraphs()) {
                content.append(paragraph.getText()).append("\n");
            }
            document.close();
            return content.toString();
        }
    }

    // 查询课程下的所有向量分块
    @Override
    public List<KnowledgeChunkDto> listChunks(Integer courseId) {
        String sql = "SELECT id, content, metadata FROM vector_store WHERE metadata->>'courseId' = ? ORDER BY (metadata->>'chunkIndex')::int";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            KnowledgeChunkDto dto = new KnowledgeChunkDto();
            dto.setId(UUID.fromString(rs.getString("id")));
            dto.setContent(rs.getString("content"));
            dto.setMetadata(rs.getString("metadata"));
            parseMetadata(dto, rs.getString("metadata"));
            return dto;
        }, String.valueOf(courseId));
    }

    // 分页查询课程下的向量分块
    @Override
    public List<KnowledgeChunkDto> listChunks(Integer courseId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        String sql = "SELECT id, content, metadata FROM vector_store WHERE metadata->>'courseId' = ? ORDER BY (metadata->>'chunkIndex')::int LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            KnowledgeChunkDto dto = new KnowledgeChunkDto();
            dto.setId(UUID.fromString(rs.getString("id")));
            dto.setContent(rs.getString("content"));
            dto.setMetadata(rs.getString("metadata"));
            parseMetadata(dto, rs.getString("metadata"));
            return dto;
        }, String.valueOf(courseId), pageSize, offset);
    }

    // 统计课程下的向量分块数量
    @Override
    public long countChunks(Integer courseId) {
        String sql = "SELECT COUNT(*) FROM vector_store WHERE metadata->>'courseId' = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, String.valueOf(courseId));
        return count != null ? count : 0L;
    }

    // 删除课程下的所有向量分块
    @Override
    public int deleteChunksByCourseId(Integer courseId) {
        String sql = "DELETE FROM vector_store WHERE metadata->>'courseId' = ?";
        return jdbcTemplate.update(sql, String.valueOf(courseId));
    }

    // 解析metadata JSON，填充courseId、chunkIndex、chunkTotal
    private void parseMetadata(KnowledgeChunkDto dto, String metadataJson) {
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            if (node.has("courseId")) {
                dto.setCourseId(node.get("courseId").asInt());
            }
            if (node.has("chunkIndex")) {
                dto.setChunkIndex(node.get("chunkIndex").asInt());
            }
            if (node.has("chunkTotal")) {
                dto.setChunkTotal(node.get("chunkTotal").asInt());
            }
        } catch (Exception e) {
            log.warn("解析metadata失败: {}", e.getMessage());
        }
    }
}
