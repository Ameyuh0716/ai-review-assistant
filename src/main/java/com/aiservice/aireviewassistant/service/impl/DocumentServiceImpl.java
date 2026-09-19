package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.dto.KnowledgeChunkDto;
import com.aiservice.aireviewassistant.dto.KnowledgeDocumentDto;
import com.aiservice.aireviewassistant.entity.KnowledgeDocument;
import com.aiservice.aireviewassistant.mapper.KnowledgeDocumentMapper;
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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    /** 中间分块保留的最小字符数（低于该值的碎片会被合并丢弃） */
    private static final int MIN_CHUNK_LENGTH = 10;

    /** 单个分块的目标最大字符数 */
    private static final int MAX_CHUNK_LENGTH = 500;

    /** 历史分块（无文件名元数据）在知识库管理页的兜底资料名称 */
    private static final String FALLBACK_DOC_NAME = "历史导入资料";

    private final VectorStore vectorStore;
    private final CoursesService coursesService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    /**
     * 构造方法：注入向量库、课程服务、JdbcTemplate 与 JSON 工具。
     *
     * @param vectorStore    Spring AI 向量存储
     * @param coursesService 课程服务，用于校验课程 ID 合法性
     * @param jdbcTemplate   JDBC 模板，用于直接操作 vector_store 表
     * @param objectMapper   JSON 解析工具，用于解析 metadata
     * @param knowledgeDocumentMapper 原始资料 Mapper，用于保存/读取完整原文
     */
    public DocumentServiceImpl(VectorStore vectorStore,
                               CoursesService coursesService,
                               JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper,
                               KnowledgeDocumentMapper knowledgeDocumentMapper) {
        this.vectorStore = vectorStore;
        this.coursesService = coursesService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
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

        // 复用文本导入逻辑完成分块与向量化；携带文件名以便知识库按资料分组展示
        return importContent(courseId, content, extractBaseName(file.getOriginalFilename()));
    }

    /**
     * 从上传文件名中提取纯文件名（去除路径前缀）。
     *
     * @param originalFilename 原始文件名，可能包含完整路径
     * @return 去除路径后的文件名；入参为空时返回 null
     */
    private String extractBaseName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        int slash = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        return (slash >= 0 ? originalFilename.substring(slash + 1) : originalFilename).trim();
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
        // 未指定文件名时，用带时间戳的默认名称，保证多次导入可区分
        String defaultName = "手动导入文本 " + java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        return importContent(courseId, content, defaultName);
    }

    /**
     * 直接导入文本内容（可指定资料名称），分块、向量化后存入向量库。
     * <p>
     * 向量化成功后会把完整原文写入 {@code knowledge_document} 表，供知识库页"查看完整源文件预览"。
     * </p>
     *
     * @param courseId 课程 ID
     * @param content  原始文本内容
     * @param fileName 资料名称（写入分块元数据与原文表，供知识库按资料分组展示）
     * @return 处理结果描述
     */
    @Override
    public String importContent(Integer courseId, String content, String fileName) {
        // 按段落对文档进行分块
        List<String> chunks = splitDocument(content);

        if (chunks.isEmpty()) {
            return "错误：文档内容为空或无法分块";
        }

        // 批量构建 Spring AI Document，并附加课程、文件与分片元数据
        // 已有分块数用于续接序号，支持对同一课程多次导入而不产生序号冲突
        int baseIndex = (int) countChunks(courseId);
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("courseId", courseId);
            if (fileName != null && !fileName.isBlank()) {
                metadata.put("fileName", fileName);
            }
            metadata.put("chunkIndex", baseIndex + i);
            metadata.put("chunkTotal", baseIndex + chunks.size());
            documents.add(new Document(chunk, metadata));
        }

        try {
            // 调用向量库批量写入：内部会自动完成文本向量化与持久化
            vectorStore.add(documents);
        } catch (Exception e) {
            log.error("文档向量化失败: {}", e.getMessage(), e);
            return "错误：文档向量化失败 - " + e.getMessage();
        }

        // 向量化成功后保存完整原文，用于知识库“预览源文件”
        saveOriginalDocument(courseId, fileName, content, chunks.size());
        return "成功：文档已分块并向量化，共处理 " + chunks.size() + " 块";
    }

    /**
     * 保存资料的完整原文。
     * <p>写入失败仅记录警告，不影响向量化结果与上传流程。</p>
     *
     * @param courseId   课程 ID
     * @param fileName   资料名称
     * @param content    解析后的完整文本
     * @param chunkCount 分块数量
     */
    private void saveOriginalDocument(Integer courseId, String fileName, String content, int chunkCount) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setCourseId(courseId);
            doc.setFileName(fileName);
            doc.setContent(content);
            doc.setChunkCount(chunkCount);
            doc.setCreatedAt(LocalDateTime.now());
            knowledgeDocumentMapper.insert(doc);
        } catch (Exception e) {
            log.warn("保存资料原文失败（不影响检索）: courseId={}, fileName={}, err={}",
                courseId, fileName, e.getMessage());
        }
    }

    /**
     * 文档分块策略：按段落（双换行符）聚合，目标单块约 500 字符。
     * <p>
     * 规则说明：
     * - 段落级聚合可保持语义完整性；
     * - 当追加新段落会超过 500 字符时，保存当前块（至少 {@value #MIN_CHUNK_LENGTH} 字符才保留，避免碎片）；
     * - 最后剩余内容非空即保留为一块（支持短文本导入）；
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

            // 如果当前块加上新段落超过上限，且当前块已积累足够内容，则保存当前块
            if (currentChunk.length() + paragraph.length() > MAX_CHUNK_LENGTH) {
                if (currentChunk.length() > MIN_CHUNK_LENGTH) {
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

        // 添加最后一个块：非空即保留（短文本片段同样可导入）
        String tail = currentChunk.toString().trim();
        if (!tail.isEmpty()) {
            chunks.add(tail);
        }

        // 限制最多 50 个块，避免向量库记录过大
        if (chunks.size() > 50) {
            chunks = chunks.subList(0, 50);
        }

        return chunks;
    }

    /**
     * 根据文件扩展名选择对应的解析器读取内容。
     * <p>
     * 采用扩展名而非 MIME 类型判断：浏览器上报的 MIME 在部分系统下会缺失（例如 macOS
     * 上传 .md 时 file.type 可能为空字符串），扩展名判断更稳定；中文文件名不影响判断。
     * </p>
     *
     * @param file 上传的多媒体文件
     * @return 文件解析后的纯文本内容
     * @throws Exception 文件名无效或不支持的格式时抛出
     */
    private String readFileContent(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        // 部分客户端会把完整路径放进文件名，这里只取最后一段；
        // 转小写必须指定 Locale.ROOT，否则在土耳其语等区域设置下字母映射会被改变
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String baseName = (slash >= 0 ? filename.substring(slash + 1) : filename).toLowerCase(Locale.ROOT);

        if (baseName.endsWith(".pdf")) {
            return readPdfContent(file);
        }
        if (baseName.endsWith(".docx")) {
            return readWordContent(file);
        }
        if (baseName.endsWith(".doc")) {
            // Apache POI 的 XWPFDocument 只能解析 OOXML(.docx)，
            // 旧版二进制 .doc 会直接解析失败，这里给出可操作的提示而非泛化的读取失败
            throw new IllegalArgumentException("暂不支持旧版 .doc 格式，请用 Word 另存为 .docx 后重新上传");
        }
        if (baseName.endsWith(".txt") || baseName.endsWith(".md") || baseName.endsWith(".markdown")) {
            return readTextContent(file);
        }
        throw new IllegalArgumentException("仅支持 txt、md、markdown、pdf、docx 格式文件");
    }

    /**
     * 读取 txt / md 等纯文本文件内容。
     * <p>
     * 优先按 UTF-8 严格解码；若字节序列不是合法 UTF-8（典型场景是 Windows 记事本保存的
     * GBK/GB2312 中文文件），则回退用 GB18030 解码，避免整篇内容存成乱码。
     * </p>
     *
     * @param file 上传的文本文件
     * @return 文件文本内容
     * @throws Exception IO 异常时抛出
     */
    private String readTextContent(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();

        // 跳过 UTF-8 BOM，否则首行会多出不可见字符
        int offset = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }

        String text = decodeText(bytes, offset);
        // 统一换行符，保证分块结果稳定（与原逐行读取行为一致）
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * 将字节数组解码为文本，UTF-8 优先，失败时回退 GB18030。
     *
     * @param bytes  原始字节
     * @param offset 起始偏移（用于跳过 BOM）
     * @return 解码后的文本
     */
    private String decodeText(byte[] bytes, int offset) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer)
                    .toString();
        } catch (CharacterCodingException e) {
            log.warn("文件不是合法 UTF-8 编码，回退使用 GB18030 解码");
            return new String(bytes, offset, bytes.length - offset, Charset.forName("GB18030"));
        }
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
     * 查询课程下的资料列表（按上传文件聚合）。
     * <p>
     * 以分块元数据中的 {@code fileName} 分组：同一文件的多个分块合并为一份资料，
     * 资料内分块按 chunkIndex 升序；无文件名元数据的历史分块归入「历史导入资料」。
     * 若该资料已保存完整原文（{@code knowledge_document}），一并返回资料 ID 与原文标记。
     * </p>
     *
     * @param courseId 课程 ID
     * @return 资料列表（按首次出现的顺序）
     */
    @Override
    public List<KnowledgeDocumentDto> listDocuments(Integer courseId) {
        // 已保存的完整原文：fileName -> 最新一条记录
        Map<String, KnowledgeDocument> originals = new java.util.LinkedHashMap<>();
        for (KnowledgeDocument doc : knowledgeDocumentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getCourseId, courseId)
                    .orderByAsc(KnowledgeDocument::getId))) {
            // 同名资料重复上传时保留最新一条（后写覆盖前写）
            originals.put(doc.getFileName(), doc);
        }

        // 复用全量分块查询：单课程分块上限 50，可一次性返回并按资料分组
        List<KnowledgeChunkDto> chunks = listChunks(courseId);
        Map<String, List<KnowledgeChunkDto>> grouped = new java.util.LinkedHashMap<>();
        for (KnowledgeChunkDto chunk : chunks) {
            String name = chunk.getFileName() != null && !chunk.getFileName().isBlank()
                ? chunk.getFileName() : FALLBACK_DOC_NAME;
            grouped.computeIfAbsent(name, k -> new ArrayList<>()).add(chunk);
        }

        List<KnowledgeDocumentDto> documents = new ArrayList<>();
        grouped.forEach((name, list) -> {
            KnowledgeDocument original = originals.get(name);
            documents.add(new KnowledgeDocumentDto(
                original != null ? original.getId() : null,
                name,
                list.size(),
                original != null,
                list
            ));
        });
        return documents;
    }

    /**
     * 查询某份资料的完整原文。
     * <p>
     * 优先读取 {@code knowledge_document} 中保存的原文；历史资料（未保存原文）则回退为
     * 将其全部分块按顺序拼接，保证任何资料都能预览到完整内容。
     * </p>
     *
     * @param courseId 课程 ID
     * @param fileName 资料名称
     * @return 完整原文；资料不存在时返回 null
     */
    @Override
    public String getDocumentContent(Integer courseId, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        KnowledgeDocument original = knowledgeDocumentMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getCourseId, courseId)
                .eq(KnowledgeDocument::getFileName, fileName)
                .orderByDesc(KnowledgeDocument::getId)
                .last("LIMIT 1"));
        if (original != null && original.getContent() != null) {
            return original.getContent();
        }
        // 历史数据回退：按分块顺序拼接为完整文本
        StringBuilder sb = new StringBuilder();
        for (KnowledgeChunkDto chunk : listChunks(courseId)) {
            String name = chunk.getFileName() != null && !chunk.getFileName().isBlank()
                ? chunk.getFileName() : FALLBACK_DOC_NAME;
            if (!name.equals(fileName)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(chunk.getContent());
        }
        return sb.length() > 0 ? sb.toString() : null;
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
     * 删除课程下的所有向量分块，同时清理已保存的资料原文。
     *
     * @param courseId 课程 ID
     * @return 受影响的分块行数
     */
    @Override
    public int deleteChunksByCourseId(Integer courseId) {
        // 一并删除原文记录，避免清空知识库后“预览原文”仍能打开
        try {
            knowledgeDocumentMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocument>()
                    .eq(KnowledgeDocument::getCourseId, courseId));
        } catch (Exception e) {
            log.warn("删除资料原文失败: courseId={}, err={}", courseId, e.getMessage());
        }
        String sql = "DELETE FROM vector_store WHERE metadata->>'courseId' = ?";
        return jdbcTemplate.update(sql, String.valueOf(courseId));
    }

    /**
     * 解析 metadata JSON，将 courseId、fileName、chunkIndex、chunkTotal 填充到 DTO。
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
            if (node.hasNonNull("fileName")) {
                dto.setFileName(node.get("fileName").asText());
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
