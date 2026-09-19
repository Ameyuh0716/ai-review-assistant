package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.QuizService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link QuizService} 的实现类。
 * <p>
 * 基于 Spring AI {@link ChatClient} 调用大模型生成题目，并通过本地正则解析与格式化逻辑，
 * 将模型返回的非结构化文本转换为统一 Markdown 格式。支持缓存、同步生成、批量生成与流式生成。
 * </p>
 * <p>
 * 出题使用<b>快速模型</b>（{@code ai.model.fast-name}，默认 qwen-turbo）：
 * 实测同样 5 道题 qwen-turbo 约 4 秒而 qwen-plus 需 17 秒以上，且 qwen-plus 输出更冗长；
 * 出题对措辞要求不高，速度收益明显。
 * </p>
 */
@Slf4j
@Service
public class QuizServiceImpl implements QuizService {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造题目生成服务。
     *
     * @param chatClient     快速模型聊天客户端（用于出题，速度优先）
     * @param promptTemplate 提示词模板渲染器，用于加载 quiz-system.txt / quiz-user.txt
     * @param objectMapper   JSON 工具，用于构造流式结束时的格式化控制帧
     */
    public QuizServiceImpl(@Qualifier("fastChatClient") ChatClient chatClient,
                           PromptTemplate promptTemplate, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateQuiz(String topic) {
        return generateQuiz(topic, 1);
    }

    @Override
    @Cacheable(value = "agentResults", key = "'quiz:' + #topic + ':' + #count")
    public String generateQuiz(String topic, int count) {
        String prompt = buildQuizPrompt(topic, count);
        String systemPrompt = promptTemplate.render("quiz-system.txt", null);
        String raw = chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .call()
            .content();
        return formatQuizOutput(raw, topic);
    }

    @Override
    public Flux<String> generateQuizStream(String topic, int count) {
        String prompt = buildQuizPrompt(topic, count);
        String systemPrompt = promptTemplate.render("quiz-system.txt", null);
        return chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .stream()
            .content()
            // 先聚合完整模型输出，再一次性格式化并返回，避免 SSE 流式传输破坏 Markdown 结构
            .collect(StringBuilder::new, StringBuilder::append)
            .flatMapMany(sb -> {
                String formatted = formatQuizOutput(sb.toString(), topic);
                // 后端统一格式化后一次性发出，由前端按片段做字符流动画，
                // 避免 SSE 按字符传输出丢失换行/缩进。
                return Flux.just(formatted);
            });
    }

    /**
     * 流式生成题目：原始 token 实时下发，全部完成后追加 JSON 格式化帧。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>调用快速模型流式接口，token 到达即转发给前端（用户立即看到生成过程，而非“白屏等待”）；</li>
     *   <li>同时用 {@link StringBuilder} 聚合完整原始输出；</li>
     *   <li>流结束后追加一帧 {@code {"__quizFinal":true,"content":"..."}}，
     *       前端据此解析出结构化题目。</li>
     * </ol>
     * </p>
     *
     * @param topic 知识点主题
     * @param count 期望生成的题目数量
     * @return 实时 token 流 + 末尾 JSON 格式化帧
     */
    @Override
    public Flux<String> generateQuizStreamWithFinal(String topic, int count) {
        String prompt = buildQuizPrompt(topic, count);
        String systemPrompt = promptTemplate.render("quiz-system.txt", null);
        StringBuilder buffer = new StringBuilder();
        Flux<String> tokenStream = chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .stream()
            .content()
            .doOnNext(buffer::append);
        // concat 保证格式化帧一定在模型输出全部结束后才发送
        return Flux.concat(tokenStream, Flux.defer(() -> {
            String formatted = formatQuizOutput(buffer.toString(), topic);
            log.debug("[Quiz] 流式出题完成：topic={}, count={}, 原始长度={}, 格式化长度={}",
                topic, count, buffer.length(), formatted.length());
            String frame;
            try {
                // content 使用 JSON 转义，保证 markdown 中的引号/换行不会破坏帧结构
                frame = "{\"__quizFinal\":true,\"content\":"
                    + objectMapper.writeValueAsString(formatted) + "}";
            } catch (Exception e) {
                log.warn("[Quiz] 序列化格式化帧失败，回退为纯文本下发: {}", e.getMessage());
                frame = formatted;
            }
            return Flux.just(frame);
        }));
    }

    /**
     * 构造用户提示词。
     *
     * @param topic 知识点主题
     * @param count 题目数量
     * @return 渲染后的用户提示词字符串
     */
    private String buildQuizPrompt(String topic, int count) {
        return promptTemplate.render("quiz-user.txt", Map.of(
            "topic", topic,
            "count", String.valueOf(count)
        ));
    }

    /**
     * 将模型返回的任意格式题目内容，统一解析并格式化为规范 Markdown。
     * <p>
     * 支持多种题目标记变体（如 {@code 【题目1】}、{@code 题目1：}、{@code ### 题目 1：} 等），
     * 并兼容选项/答案/解析挤在同一行的非标准输出。
     * </p>
     *
     * @param raw   模型返回的原始文本
     * @param topic 知识点主题
     * @return 包含答案与解析的标准 Markdown 题目文本
     */
    public String formatQuizOutput(String raw, String topic) {
        return formatQuizOutput(raw, topic, false);
    }

    /**
     * 将模型返回的题目内容格式化为供对话展示的无答案 Markdown，隐藏答案与解析。
     *
     * @param raw   模型返回的原始文本
     * @param topic 知识点主题
     * @return 隐藏答案与解析后的 Markdown 题目文本
     */
    public String formatQuizOutputForChat(String raw, String topic) {
        return formatQuizOutput(raw, topic, true);
    }

    /**
     * 题目格式化的核心实现。
     *
     * @param raw        模型返回的原始文本
     * @param topic      知识点主题
     * @param hideAnswer 是否隐藏答案与解析（对话展示场景）
     * @return 规范化后的 Markdown 题目文本
     */
    private String formatQuizOutput(String raw, String topic, boolean hideAnswer) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }

        // 1. 先尝试找到引导语（第一个题目标记之前的部分），并去掉末尾分隔线
        String intro = "";
        String body = raw;
        Pattern firstQuestionPattern = Pattern.compile("(?:###\\s*题目\\s*\\d+[：:]|[【\\[]题目\\d+[】\\]]|题目\\s*\\d+[：:])");
        Matcher firstQuestionMatcher = firstQuestionPattern.matcher(raw);
        if (firstQuestionMatcher.find()) {
            if (firstQuestionMatcher.start() > 0) {
                intro = raw.substring(0, firstQuestionMatcher.start()).trim().replaceAll("\\s*-{3,}\\s*$", "").trim();
            }
            body = raw.substring(firstQuestionMatcher.start());
        }

        // 2. 把题目分块
        List<String> blocks = splitQuestions(body);
        if (blocks.isEmpty()) {
            log.warn("[Quiz] 未从模型输出中识别出题目标记，返回原始内容。topic={}", topic);
            return raw;
        }

        // 3. 逐个解析并重整
        StringBuilder result = new StringBuilder();
        if (!intro.isEmpty()) {
            result.append(intro).append("\n\n");
        }

        int index = 1;
        for (String block : blocks) {
            Question question;
            try {
                question = parseQuestion(block, topic);
            } catch (Exception e) {
                log.warn("[Quiz] 解析第 {} 块题目失败，跳过。topic={}", index, topic, e);
                continue;
            }
            if (question == null) continue;

            if (index > 1) {
                result.append("---\n\n");
            }
            result.append("### 题目 ").append(index).append("：").append(question.subject).append("\n\n")
                .append(question.text).append("\n\n");
            for (String opt : question.options) {
                result.append(opt).append("\n");
            }
            if (!hideAnswer) {
                result.append("\n**答案：").append(question.answer).append("**\n\n")
                    .append("**解析：**\n");
                for (String exp : question.explanations) {
                    result.append("- ").append(exp).append("\n");
                }
            }
            index++;
        }

        String formatted = result.toString().trim();
        int actualCount = Math.max(0, index - 1);
        // 解析零题时不能返回空串：保留模型原文，避免前端拿到空白内容后“生成测验无反应”
        if (actualCount == 0) {
            log.warn("[Quiz] 未解析出有效题目（块数={}），返回模型原文。topic={}", blocks.size(), topic);
            return raw.trim();
        }
        log.debug("[Quiz] 格式化完成：topic={}, hideAnswer={}, 原始块数={}, 有效题目数={}", topic, hideAnswer, blocks.size(), actualCount);
        return formatted;
    }

    /**
     * 按题目标记将模型输出拆分为独立题目块。
     * <p>
     * 支持如下标记变体：
     * <ul>
     *   <li>{@code ### 题目 1：}</li>
     *   <li>{@code ###题目1：}</li>
     *   <li>{@code 【题目1】}</li>
     *   <li>{@code 题目1：}</li>
     *   <li>{@code 题目 1：}</li>
     * </ul>
     * 若未识别到任何标记，则将整段文本视为一道题目。
     * </p>
     *
     * @param body 去除引导语后的题目正文
     * @return 拆分后的题目块列表
     */
    private List<String> splitQuestions(String body) {
        List<String> blocks = new ArrayList<>();
        // 正则说明：
        // (?:---\s*)?       可选的前置分隔线
        // (?:###\s*题目\s*\d+[：:])  匹配 "### 题目 1：" 及其空格变体
        // |[【\[]题目\d+[】\]]      匹配 "【题目1】" 或 "[题目1]"
        // |题目\s*\d+[：:]          匹配 "题目1：" 或 "题目 1："
        Pattern pattern = Pattern.compile("(?:---\\s*)?(?:###\\s*题目\\s*\\d+[：:]|[【\\[]题目\\d+[】\\]]|题目\\s*\\d+[：:])");
        Matcher matcher = pattern.matcher(body);

        // 收集每个题目标记在原文中的起始位置
        List<Integer> starts = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
        }

        if (starts.isEmpty()) {
            // 没有检测到任何题目标记，可能整段就是一道题
            String trimmed = body.replaceAll("^---\\s*", "").trim();
            if (!trimmed.isEmpty()) blocks.add(trimmed);
            return blocks;
        }

        // 按相邻标记位置截取题目块
        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : body.length();
            String block = body.substring(start, end).replaceAll("^---\\s*", "").trim();
            if (!block.isEmpty()) blocks.add(block);
        }
        return blocks;
    }

    /**
     * 解析单个题目块，提取题干、选项、答案、解析与学科名称。
     * <p>
     * 解析顺序：题目标记 → 答案 → 解析 → 选项 → 题干 → 学科名。
     * 通过正则在模型非标准输出中尽量稳定地抽取结构化字段；若最终未识别到选项，则返回 {@code null}。
     * </p>
     *
     * @param block         单个题目文本块
     * @param defaultSubject 默认学科名称，通常为知识点 topic
     * @return 解析后的题目对象；解析失败或无选项时返回 {@code null}
     */
    private Question parseQuestion(String block, String defaultSubject) {
        // 去掉题目标记行（兼容 ### 题目 1： / 【题目1】 / 题目 1： 等）
        String content = block.replaceFirst("^(?:###\\s*)?题目\\s*\\d+[：:]", "")
                              .replaceFirst("^[【\\[]题目\\d+[】\\]]", "")
                              .trim();

        // 提取答案：匹配 "答案：A" 或 "**答案：A**" 等加粗变体，限定选项为 A-D
        String answer = "";
        Pattern answerPattern = Pattern.compile("(?:\\*\\*|\\s)*答案[：:]([A-Da-d])(?:\\*\\*|\\s)*");
        Matcher answerMatcher = answerPattern.matcher(content);
        if (answerMatcher.find()) {
            answer = answerMatcher.group(1).toUpperCase();
            // 从内容中移除答案段落，避免干扰后续题干与选项提取
            content = content.substring(0, answerMatcher.start()) + content.substring(answerMatcher.end());
        }

        // 提取解析：从"解析"开始到结尾，并去掉可能混入的末尾分隔线
        String explanationText = "";
        Pattern expPattern = Pattern.compile("(?:\\*\\*|\\s)*解析[：:](.*)$", Pattern.DOTALL);
        Matcher expMatcher = expPattern.matcher(content);
        if (expMatcher.find()) {
            explanationText = expMatcher.group(1).trim().replaceAll("\\s*-{3,}\\s*$", "").trim();
            content = content.substring(0, expMatcher.start()).trim();
        }

        // 提取选项 A/B/C/D：支持 A. / A、 / A． 及同行挤在一起、跨行的情况
        List<String> options = new ArrayList<>();
        Pattern optPattern = Pattern.compile("([A-Da-d])[.．、]\\s*(.*?)(?=(?:[A-Da-d][.．、])|(?:答案)|(?:解析)|$)", Pattern.DOTALL);
        Matcher optMatcher = optPattern.matcher(content);
        int firstOptStart = -1;
        while (optMatcher.find()) {
            if (firstOptStart < 0) {
                firstOptStart = optMatcher.start();
            }
            String label = optMatcher.group(1).toUpperCase();
            // 把选项内部的换行、多余空白压缩成单行，避免前端渲染错乱
            String text = optMatcher.group(2).replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            options.add(label + ". " + text);
        }

        // 题干 = 选项前的内容
        String questionText = content;
        if (firstOptStart >= 0) {
            questionText = content.substring(0, firstOptStart).trim();
        }
        // 清理题干末尾可能残留的单个选项标记
        questionText = questionText.replaceAll("[A-Da-d][.．、]\\s*$", "").trim();

        // 学科名称：优先用 defaultSubject；若题干首行是短标题则提取为学科
        String subject = defaultSubject;
        String[] lines = questionText.split("\\s*\\n\\s*", 2);
        if (lines.length >= 1 && !lines[0].isEmpty()) {
            String firstLine = lines[0].trim();
            // 首行在 2~14 字之间、且以标点结尾，视为学科标题
            if (firstLine.length() >= 2 && firstLine.length() <= 14
                && (firstLine.endsWith("：") || firstLine.endsWith(":")
                    || firstLine.endsWith("，") || firstLine.endsWith(",")
                    || firstLine.endsWith("。") || firstLine.endsWith("."))) {
                subject = firstLine.substring(0, firstLine.length() - 1).trim();
                questionText = questionText.substring(firstLine.length()).trim();
            }
            // 若首行就是学科名（无标点）且与 topic 相近，也视为标题
            else if (firstLine.length() >= 2 && firstLine.length() <= 14
                     && !firstLine.contains("？") && !firstLine.contains("?")
                     && (firstLine.contains(defaultSubject) || defaultSubject.contains(firstLine))) {
                subject = firstLine;
                questionText = questionText.substring(firstLine.length()).trim();
            }
        }
        // 清理题干中重复出现的学科前缀，并把题干整理成连贯段落
        questionText = questionText.replaceFirst("^(?:" + Pattern.quote(subject) + "[：:]?\\s*)", "")
                                   .replaceAll("\\s*\\n\\s*", " ")
                                   .trim();

        // 解析拆成列表，清理 markdown 列表/加粗标记
        List<String> explanations = new ArrayList<>();
        if (!explanationText.isEmpty()) {
            String[] expLines = explanationText.split("\\r?\\n|(?=-[\\s-])");
            for (String line : expLines) {
                line = line.replaceAll("^[-*#]+\\s*", "")
                           .replaceAll("^\\*\\*\\s*", "")
                           .replaceAll("\\s*\\*\\*$", "")
                           .trim();
                if (line.length() >= 2) explanations.add(line);
            }
        }
        if (explanations.isEmpty()) {
            explanations.add("略");
        }

        // 如果没有选项，说明模型输出不符合选择题格式，放弃解析
        if (options.isEmpty()) return null;

        return new Question(subject, questionText, options, answer, explanations);
    }

    /**
     * 题目结构化数据记录。
     *
     * @param subject      学科/知识点名称
     * @param text         题干文本
     * @param options      选项列表，每项形如 "A. xxx"
     * @param answer       答案字母（大写），可能为空
     * @param explanations 解析要点列表
     */
    private record Question(String subject, String text, List<String> options, String answer, List<String> explanations) {}
}
