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

/**
 * 知识库文档服务实现类。
 * <p>
 * 负责课程文档的全生命周期处理：
 * 1. 接收上传文件或直接文本，解析为纯文本（支持 txt、md、pdf、doc、docx）；
 * 2. 按段落进行文本分块，控制单块大小并限制总块数；
 * 3. 将分块包装为 Spring AI Document 并写入 PgVector 向量库；
 * 4. 提供向量分块的查询、统计、删除能力（通过 JdbcTemplate 直接访问 vector_store 表）。
 * </p>
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final VectorStore vectorStore;
    private final CoursesService coursesService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造方法：注入向量库、课程服务、JdbcTemplate 与 JSON 工具。
     *
     * @param vectorStore    Spring AI 向量存储
     * @param coursesService 课程服务，用于校验课程 ID 合法性
     * @param jdbcTemplate   JDBC 模板，用于直接操作 vector_store 表
     * @param objectMapper   JSON 解析工具，用于解析 metadata
     */
    public DocumentServiceImpl(VectorStore vectorStore,
                               CoursesService coursesService,
                               JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.coursesService = coursesService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传文档并解析、分块、向量化后存入向量库。
     * <p>
     * 处理流程：校验课程 → 读取/解析文件内容 → 调用 {@link #importContent} 完成分块与向量化。
     * </p>
     *
     * @param courseId 课程 ID
     * @param file     上传的文件
     * @return 处理结果描述
     */
    @Override
    public String uploadAndVectorize(Integer courseId, MultipartFile file){
        // 校验课程是否存在，避免向不存在的课程写入脏数据
        if (coursesService.getById(courseId) == null) {
            return "错误：课程ID不存在";
        }

        // 读取并解析文件内容，异常时返回可读错误信息
        String content;
        try {
            content = readFileContent(file);
        } catch (Exception e) {
            return "错误：读取文件失败 - " + e.getMessage();
        }

        // 复用文本导入逻辑完成分块与向量化
        return importContent(courseId, content);
    }

    /**
     * 直接导入文本内容，分块、向量化后存入向量库。
     *
     * @param courseId 课程 ID
     * @param content  原始文本内容
     * @return 处理结果描述
     */
    @Override
    public String importContent(Integer courseId, String content) {
        // 按段落对文档进行分块
        List<String> chunks = splitDocument(content);

        if (chunks.isEmpty()) {
            return "错误：文档内容为空或无法分块";
        }

        // 批量构建 Spring AI Document，并附加课程与分片元数据
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
            // 调用向量库批量写入：内部会自动完成文本向量化与持久化
            vectorStore.add(documents);
            return "成功：文档已分块并向量化，共处理 " + chunks.size() + " 块";
        } catch (Exception e) {
            log.error("文档向量化失败: {}", e.getMessage(), e);
            return "错误：文档向量化失败 - " + e.getMessage();
        }
    }

    /**
     * 文档分块策略：按段落（双换行符）聚合，目标单块约 500 字符。
     * <p>
     * 规则说明：
     * - 段落级聚合可保持语义完整性；
     * - 当追加新段落会超过 500 字符时，保存当前块（至少 50 字符才保留，避免碎片）；
     * - 最后剩余内容若大于 50 字符也作为一块；
     * - 整体限制最多 50 个块，防止超长文档占用过多向量空间。
     * </p>
     *
     * @param content 原始文本内容
     * @return 分块后的文本列表
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

            // 如果当前块加上新段落超过 500 字符，且当前块已积累足够内容，则保存当前块
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

        // 限制最多 50 个块，避免向量库记录过大
        if (chunks.size() > 50) {
            chunks = chunks.subList(0, 50);
        }

        return chunks;
    }

    /**
     * 根据文件扩展名选择对应的解析器读取内容。
     *
     * @param file 上传的多媒体文件
     * @return 文件解析后的纯文本内容
     * @throws Exception 文件名无效或不支持的格式时抛出
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

    /**
     * 读取 txt / md 等纯文本文件内容，按 UTF-8 编码逐行读取。
     *
     * @param file 上传的文本文件
     * @return 文件文本内容
     * @throws Exception IO 异常时抛出
     */
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

    /**
     * 读取 PDF 文件内容，使用 Apache PDFBox 提取文本。
     *
     * @param file 上传的 PDF 文件
     * @return PDF 中的文本内容
     * @throws Exception 解析异常时抛出
     */
    private String readPdfContent(MultipartFile file) throws Exception {
        org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes());
        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
        String text = stripper.getText(document);
        document.close();
        return text;
    }

    /**
     * 读取 Word 文件内容，使用 Apache POI 提取段落文本。
     *
     * @param file 上传的 doc/docx 文件
     * @return Word 文档中的文本内容
     * @throws Exception 解析异常时抛出
     */
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

    /**
     * 查询课程下的全部向量分块，按 chunkIndex 升序排列。
     * <p>
     * 通过 metadata JSON 中的 courseId 字段过滤，适用于 PgVector 默认表结构。
     * </p>
     *
     * @param courseId 课程 ID
     * @return 知识分块列表
     */
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

    /**
     * 分页查询课程下的向量分块，按 chunkIndex 升序排列。
     *
     * @param courseId 课程 ID
     * @param page     页码，从 1 开始
     * @param pageSize 每页大小
     * @return 指定页的知识分块列表
     */
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

    /**
     * 统计课程下的向量分块数量。
     *
     * @param courseId 课程 ID
     * @return 向量分块总数
     */
    @Override
    public long countChunks(Integer courseId) {
        String sql = "SELECT COUNT(*) FROM vector_store WHERE metadata->>'courseId' = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, String.valueOf(courseId));
        return count != null ? count : 0L;
    }

    /**
     * 删除课程下的所有向量分块。
     *
     * @param courseId 课程 ID
     * @return 受影响的行数
     */
    @Override
    public int deleteChunksByCourseId(Integer courseId) {
        String sql = "DELETE FROM vector_store WHERE metadata->>'courseId' = ?";
        return jdbcTemplate.update(sql, String.valueOf(courseId));
    }

    /**
     * 解析 metadata JSON，将 courseId、chunkIndex、chunkTotal 填充到 DTO。
     *
     * @param dto          知识分块 DTO
     * @param metadataJson metadata 原始 JSON 字符串
     */
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
