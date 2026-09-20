package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.dto.PlanDayDto;
import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.aiservice.aireviewassistant.exception.BusinessException;
import com.aiservice.aireviewassistant.mapper.StudyPlanMapper;
import com.aiservice.aireviewassistant.service.QuizService;
import com.aiservice.aireviewassistant.service.RagService;
import com.aiservice.aireviewassistant.service.StudyPlanService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学习计划服务实现类。
 * <p>
 * 实现 {@link StudyPlanService} 接口：除计划保存与查询外，还支持
 * 1. 从计划正文解析每日结构（{@link #parseDays(String)}）；
 * 2. 保存每日环节的勾选进度（{@link #updateProgress}）；
 * 3. 按天按环节流式生成学习材料（{@link #generateDaySection}），生成完成后自动持久化。
 * </p>
 */
@Slf4j
@Service
public class StudyPlanServiceImpl extends ServiceImpl<StudyPlanMapper, StudyPlan> implements StudyPlanService {

    /** 支持的生成环节。 */
    private static final Set<String> SECTIONS = Set.of("review", "mastery", "practice");

    /** 生成完成控制帧：前端收到后主动关闭 SSE 连接，避免浏览器将流正常结束记为错误。 */
    private static final String DONE_FRAME = "{\"__done\":true}";

    /** 练习环节每次生成的题目数量。 */
    private static final int PRACTICE_QUIZ_COUNT = 3;

    /** 单日要点上限，避免异常计划产生过多条目。 */
    private static final int MAX_ITEMS_PER_DAY = 12;

    /** 每日标题最大长度，超过则视为普通正文而非日程标题。 */
    private static final int MAX_TITLE_LENGTH = 50;

    /**
     * 日程标题正则：兼容 {@code ## 第1天：标题}、{@code 1. **第1天：标题**}、{@code **Day 1 标题**}、
     * {@code 第三天：标题} 等变体。
     * <p>组 1：阿拉伯数字天数；组 2：英文 Day N；组 3：中文数字天数；组 4：标题。</p>
     */
    private static final Pattern DAY_HEADER = Pattern.compile(
        "^\\s*(?:#{1,6}\\s*)?(?:\\d{1,2}\\s*[.、)）]\\s*)?\\*{0,2}\\s*"
            + "(?:第\\s*(\\d{1,2})\\s*[天日]|Day\\s*(\\d{1,2})\\b|第\\s*([一二三四五六七八九十]{1,3})\\s*[天日])"
            + "\\s*[*：:.、\\-—]?\\s*(.*?)\\s*\\*{0,2}\\s*$",
        Pattern.CASE_INSENSITIVE);

    /** 列表项正则：{@code - xxx}、{@code * xxx}、{@code 1. xxx}、{@code 1、xxx} 等。 */
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*+]|\\d{1,2}\\s*[.、)）])\\s+(.+)$");

    /** 中文数字到阿拉伯数字的映射（覆盖计划中常见的 1-20 天）。 */
    private static final Map<String, Integer> CN_NUMBERS = new LinkedHashMap<>();

    static {
        CN_NUMBERS.put("一", 1);
        CN_NUMBERS.put("二", 2);
        CN_NUMBERS.put("三", 3);
        CN_NUMBERS.put("四", 4);
        CN_NUMBERS.put("五", 5);
        CN_NUMBERS.put("六", 6);
        CN_NUMBERS.put("七", 7);
        CN_NUMBERS.put("八", 8);
        CN_NUMBERS.put("九", 9);
        CN_NUMBERS.put("十", 10);
        CN_NUMBERS.put("十一", 11);
        CN_NUMBERS.put("十二", 12);
        CN_NUMBERS.put("十三", 13);
        CN_NUMBERS.put("十四", 14);
        CN_NUMBERS.put("十五", 15);
        CN_NUMBERS.put("十六", 16);
        CN_NUMBERS.put("十七", 17);
        CN_NUMBERS.put("十八", 18);
        CN_NUMBERS.put("十九", 19);
        CN_NUMBERS.put("二十", 20);
    }

    private final ChatClient chatClient;
    private final RagService ragService;
    private final QuizService quizService;
    private final PromptTemplate promptTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造学习计划服务。
     *
     * @param chatClient     快速模型聊天客户端（按天生成材料，速度优先）
     * @param ragService     RAG 服务，用于检索知识库上下文支撑生成内容
     * @param quizService    测验服务，练习环节复用其出题能力
     * @param promptTemplate 提示词模板渲染器
     * @param objectMapper   JSON 工具，用于读写结构化进度
     */
    public StudyPlanServiceImpl(@Qualifier("fastChatClient") ChatClient chatClient,
                                RagService ragService, QuizService quizService,
                                PromptTemplate promptTemplate, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.quizService = quizService;
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存一条学习计划。
     * <p>
     * 对课程名与可用天数做去空处理；若可用天数为纯数字，则自动追加“天”单位，
     * 例如 "5" 会规范为 "5天"。创建时间与更新时间均设为当前时间。
     * </p>
     *
     * @param userId        用户 ID
     * @param courseName    课程名称
     * @param availableDays 可用天数描述
     * @param content       学习计划内容
     * @return 保存后的学习计划实体
     */
    @Override
    public StudyPlan savePlan(Integer userId, String courseName, String availableDays, String content) {
        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setCourseName(courseName != null ? courseName.trim() : "");
        String days = availableDays != null ? availableDays.trim() : "";
        // 规范化天数显示：纯数字自动补全为 "X天"，保持前端展示一致性
        if (days.matches("\\d+")) {
            days = days + "天";
        }
        plan.setAvailableDays(days);
        plan.setContent(content);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        save(plan);
        plan.setDays(parseDays(content));
        return plan;
    }

    /**
     * 根据用户 ID 查询学习计划，按创建时间倒序返回，并填充每日结构。
     *
     * @param userId 用户 ID
     * @return 学习计划列表
     */
    @Override
    public List<StudyPlan> listByUserId(Integer userId) {
        LambdaQueryWrapper<StudyPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyPlan::getUserId, userId);
        wrapper.orderByDesc(StudyPlan::getCreatedAt);
        List<StudyPlan> plans = list(wrapper);
        plans.forEach(p -> p.setDays(parseDays(p.getContent())));
        return plans;
    }

    /**
     * 从计划 Markdown 正文中解析每日结构。
     * <p>
     * 逐行扫描：命中日程标题行时开启新的一天；其后的列表项与正文行作为要点收集，
     * 直到遇到下一条日程标题。标题过长（超过 {@value #MAX_TITLE_LENGTH} 字）的行不视为日程标题，
     * 避免把正文中的"第1天…"句子误判为标题。
     * </p>
     *
     * @param content 计划正文
     * @return 每日结构列表（按天数升序）；无法识别日程时返回空列表
     */
    @Override
    public List<PlanDayDto> parseDays(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<PlanDayDto> days = new ArrayList<>();
        Integer currentDay = null;
        int currentDayLevel = 7;
        String currentTitle = "";
        List<String> currentItems = new ArrayList<>();

        for (String rawLine : content.split("\\r?\\n")) {
            String line = rawLine.trim();
            Matcher header = DAY_HEADER.matcher(line);
            if (header.matches() && header.group(4).length() <= MAX_TITLE_LENGTH) {
                // 收束上一天
                if (currentDay != null) {
                    days.add(new PlanDayDto(currentDay, currentTitle, List.copyOf(currentItems)));
                }
                currentDay = resolveDayNumber(header);
                currentDayLevel = countHeaderLevel(line);
                currentTitle = header.group(4).trim();
                currentItems = new ArrayList<>();
                continue;
            }
            if (currentDay == null || line.isEmpty()) {
                continue;
            }
            // 跳过分隔线、引用与表格行
            if (line.startsWith("---") || line.startsWith(">") || line.startsWith("|")) {
                continue;
            }
            // 非日程的 markdown 标题：同级或更高级别（如"## 复习提示"）表示当天内容已结束，
            // 更低级别（如"### 上午任务"）则视为当天的小节标题，继续收集其要点
            if (line.startsWith("#")) {
                if (countHeaderLevel(line) <= currentDayLevel) {
                    days.add(new PlanDayDto(currentDay, currentTitle, List.copyOf(currentItems)));
                    currentDay = null;
                }
                continue;
            }
            if (currentItems.size() >= MAX_ITEMS_PER_DAY) {
                continue;
            }
            Matcher item = LIST_ITEM.matcher(line);
            if (item.matches()) {
                currentItems.add(cleanItemText(item.group(1)));
            } else if (line.length() > 2) {
                // 非列表的正文段落同样作为当日要点
                currentItems.add(cleanItemText(line));
            }
        }
        // 收束最后一天
        if (currentDay != null) {
            days.add(new PlanDayDto(currentDay, currentTitle, List.copyOf(currentItems)));
        }
        // 去重：同一序号只保留首次出现的日程（模型偶尔重复输出）
        Map<Integer, PlanDayDto> unique = new LinkedHashMap<>();
        for (PlanDayDto d : days) {
            unique.putIfAbsent(d.day(), d);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * 解析日程标题中的天数序号：优先阿拉伯数字，其次英文 Day N，最后中文数字。
     *
     * @param header 已匹配的日程标题（组 1/2/3 分别为三种写法）
     * @return 天数序号；无法解析时返回 0
     */
    private int resolveDayNumber(Matcher header) {
        if (header.group(1) != null) {
            return Integer.parseInt(header.group(1));
        }
        if (header.group(2) != null) {
            return Integer.parseInt(header.group(2));
        }
        String cn = header.group(3);
        if (cn != null) {
            Integer mapped = CN_NUMBERS.get(cn);
            if (mapped != null) {
                return mapped;
            }
        }
        return 0;
    }

    /**
     * 统计 markdown 标题的层级（前导 # 个数）。
     *
     * @param line 文本行
     * @return 层级数；非标题行返回 0
     */
    private int countHeaderLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return level;
    }

    /**
     * 清理要点文本中的 markdown 强调标记与多余空白。
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    private String cleanItemText(String text) {
        return text.replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                   .replaceAll("`(.+?)`", "$1")
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * 更新计划的结构化进度。
     *
     * @param userId   用户 ID（用于归属校验）
     * @param planId   计划 ID
     * @param progress 进度 JSON 字符串
     * @return 更新后的计划；计划不存在或不属于该用户时返回 null
     */
    @Override
    public StudyPlan updateProgress(Integer userId, Integer planId, String progress) {
        StudyPlan plan = getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            return null;
        }
        // 校验 JSON 合法性，避免写入脏数据导致前端解析失败
        try {
            objectMapper.readTree(progress);
        } catch (Exception e) {
            throw new BusinessException("进度数据格式非法");
        }
        plan.setProgress(progress);
        plan.setUpdatedAt(LocalDateTime.now());
        updateById(plan);
        plan.setDays(parseDays(plan.getContent()));
        return plan;
    }

    /**
     * 流式生成某天某环节的学习材料，并在完成后写入结构化进度。
     *
     * @param userId  用户 ID（用于归属校验）
     * @param planId  计划 ID
     * @param day     天数序号
     * @param section 环节名称（review / mastery / practice）
     * @return 流式文本；校验失败时返回一次性提示文本
     */
    @Override
    public Flux<String> generateDaySection(Integer userId, Integer planId, Integer day, String section) {
        StudyPlan plan = getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            return Flux.just("计划不存在或无权访问");
        }
        String sec = section == null ? "" : section.trim().toLowerCase();
        if (!SECTIONS.contains(sec)) {
            return Flux.just("不支持的生成类型：" + section);
        }
        if (day == null || day < 1) {
            return Flux.just("天数不合法");
        }
        PlanDayDto target = parseDays(plan.getContent()).stream()
            .filter(d -> d.day().equals(day))
            .findFirst()
            .orElse(null);
        if (target == null) {
            return Flux.just("未找到第 " + day + " 天的日程结构，请检查计划内容");
        }
        String topic = target.title() != null && !target.title().isBlank()
            ? target.title() : plan.getCourseName();
        String items = target.items().isEmpty() ? "（无）" : String.join("；", target.items());

        // 练习环节：复用测验服务出题（快速模型），生成后一次性下发
        if ("practice".equals(sec)) {
            return Flux.defer(() -> {
                String content = quizService.generateQuiz(topic, PRACTICE_QUIZ_COUNT);
                saveSection(planId, day, sec, content);
                return Flux.just(content == null ? "" : content, DONE_FRAME);
            }).subscribeOn(Schedulers.boundedElastic());
        }

        // 复习内容 / 掌握内容：检索知识库上下文后流式生成
        // 限定在「该用户自己的课程」范围内检索，与其他用户的知识库隔离
        String context = ragService.retrieveContext(topic, null, RagService.RagScope.ofUser(userId));
        String template = "review".equals(sec) ? "plan-day-review.txt" : "plan-day-mastery.txt";
        String systemPrompt = promptTemplate.render(template, Map.of(
            "course", plan.getCourseName() == null ? "" : plan.getCourseName(),
            "day", String.valueOf(day),
            "topic", topic,
            "items", items,
            "context", context == null || context.isBlank()
                ? "（知识库暂无相关资料，请基于通用知识生成，不要编造具体教材页码）" : context
        ));
        StringBuilder buffer = new StringBuilder();
        Flux<String> contentStream = chatClient.prompt()
            .system(systemPrompt)
            .user("请生成内容")
            .stream()
            .content()
            .doOnNext(buffer::append)
            .doOnComplete(() -> {
                String content = buffer.toString().trim();
                if (!content.isEmpty()) {
                    saveSection(planId, day, sec, content);
                }
            });
        // 内容流结束后追加完成控制帧：前端据此关闭连接并刷新，而非依赖连接异常终止
        return Flux.concat(contentStream, Flux.defer(() -> Flux.just(DONE_FRAME)));
    }

    /**
     * 将生成内容写入计划的结构化进度并持久化。
     * <p>重新读取最新记录后再合并，尽可能减少与勾选操作的并发覆盖。</p>
     *
     * @param planId  计划 ID
     * @param day     天数序号
     * @param section 环节名称
     * @param content 生成的内容
     */
    private void saveSection(Integer planId, Integer day, String section, String content) {
        try {
            StudyPlan fresh = getById(planId);
            if (fresh == null) {
                return;
            }
            ObjectNode root = readProgressNode(fresh.getProgress());
            JsonNode dayNode = root.path(String.valueOf(day));
            ObjectNode targetDay = dayNode.isObject() ? (ObjectNode) dayNode : root.putObject(String.valueOf(day));
            ObjectNode sectionNode = targetDay.putObject(section);
            sectionNode.put("content", content);
            sectionNode.put("at", LocalDateTime.now().toString());
            fresh.setProgress(objectMapper.writeValueAsString(root));
            fresh.setUpdatedAt(LocalDateTime.now());
            updateById(fresh);
            log.debug("[Plan] 第 {} 天 {} 内容已保存，planId={}, 长度={}", day, section, planId, content.length());
        } catch (Exception e) {
            // 保存失败不影响已推送给用户的内容
            log.warn("[Plan] 保存生成内容失败: planId={}, day={}, section={}, err={}",
                planId, day, section, e.getMessage());
        }
    }

    /**
     * 读取进度 JSON 根节点；为空或非法时返回空对象树。
     *
     * @param progress 进度 JSON 字符串
     * @return 可写的根节点
     */
    private ObjectNode readProgressNode(String progress) {
        if (progress != null && !progress.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(progress);
                if (node instanceof ObjectNode objectNode) {
                    return objectNode;
                }
            } catch (Exception ignored) {
                // 非法 JSON 视为空进度重建
            }
        }
        return objectMapper.createObjectNode();
    }
}
