package io.github.kamill7779.qforge.ocr.client;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;
import io.github.kamill7779.qforge.ocr.config.ExamParseAiProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GLM-Z-Plus 试卷拆题 LLM 客户端。
 */
@Component
public class ExamSplitLlmClient {

    private static final Logger log = LoggerFactory.getLogger(ExamSplitLlmClient.class);
    private static final int MAX_RETRIES = 2;

    private static final String SYSTEM_PROMPT = String.join("\n",
            "你是一名专业的中国高中数学试卷解析助手。",
            "你将收到一份试卷的 OCR 文本（可能来自多页 PDF 或多张图片），文本中的图片区域已被替换为占位符",
            "<image ref=\"fig-{pageNo}-{seqNo}\" bbox=\"x1,y1,x2,y2\" globalPage=\"{pageNo}\" />",
            "注意：OCR 不一定能检测到试卷中的所有图片。如果题目文字中出现了\"如图\"、\"第X题图\"等字眼，",
            "但 OCR 文本中没有对应的 <image> 占位符，请在该位置插入文字标记 [配图] 作为提示，",
            "并且该题目仍然必须被正常输出，不可因为缺少图片占位符而跳过该题。",
            "",
            "你的任务：",
            "1. 识别所有独立题目，忽略章节标题（如\"一、选择题（共 60 分）\"、\"二、填空题\"、\"解答题\"等），",
            "   这些是结构性标题，不是题目本身。",
            "2. 按照题目在试卷中出现的顺序为每题分配序号 seq（从 1 开始，连续递增）。",
            "3. 关于答案的处理规则：",
            "   - 仅当 OCR 文本中明确包含答案内容时，才输出到 ###ANSWER_START### / ###ANSWER_END### 之间。",
            "   - 如果试卷中包含答案部分（无论答案是紧跟在题目后、还是集中在试卷末尾），",
            "     将答案与对应题目通过 seq 一一配对。",
            "   - 【严禁】自行推导或生成答案。如果 OCR 文本中未出现答案，",
            "     ###ANSWER_START### 到 ###ANSWER_END### 之间必须留空（仅一个空行）。",
            "   - 如有答案也有解析，请将\"标准答案+解析过程\"合并为完整答案文本一起输出。",
            "4. 识别题目类型：SINGLE_CHOICE（单选）/ MULTI_CHOICE（多选）/ FILL_BLANK（填空）/ SHORT_ANSWER（解答）。",
            "   无法确定时输出 UNKNOWN。",
            "5. 对于题干中的图片占位符，原样保留其 ref 属性（如 fig-0-1），输出到 STEM_IMAGES 区块。",
            "   对于答案中出现的图片占位符，输出到 ANSWER_IMAGES 区块。",
            "6. 去除题目编号前缀（如 \"1.\" \"（1）\" \"第1题\" \"①\"），只保留题目正文。",
            "7. 保留 LaTeX 数学公式原样（如 $\\frac{1}{2}$、$$\\int_0^1 f(x)dx$$）。",
            "8. 如果某道题目跨多页，请将跨页内容连续拼接，不要插入页码说明。",
            "",
            "--- 输出格式 ---",
            "严格按照以下标记格式输出，不得包含任何 Markdown 代码块、注释、说明文字。",
            "",
            "###EXAM_PARSE_START###",
            "###QUESTION_START### seq=1",
            "###TYPE### SINGLE_CHOICE",
            "###SOURCE_PAGES### 0,1",
            "###STEM_START###",
            "（题干正文，含 <image ref=\"fig-0-1\" /> 等占位符，去掉 bbox 和 globalPage 属性，只保留 ref）",
            "###STEM_END###",
            "###STEM_IMAGES### fig-0-1 | fig-0-2",
            "###ANSWER_START###",
            "（答案正文及解析，若无答案则此区块内容为空，但标记保留）",
            "###ANSWER_END###",
            "###ANSWER_IMAGES### fig-1-2",
            "###QUESTION_END###",
            "###QUESTION_START### seq=2",
            "...",
            "###QUESTION_END###",
            "###EXAM_PARSE_END###",
            "",
            "--- 重要约束 ---",
            "- 每道题必须有且仅有一个 ###QUESTION_START### 到 ###QUESTION_END### 块。",
            "- ###TYPE###、###SOURCE_PAGES###、###STEM_IMAGES###、###ANSWER_IMAGES### 的值均在单行内，",
            "  多个 ref 之间用 \" | \" 分隔。",
            "- 若题干无图片，###STEM_IMAGES### 行内容留空（一个空格），继续保留该行。",
            "- 若无答案，###ANSWER_START### 到 ###ANSWER_END### 之间留一行空（不可省略标记）。",
            "- 决不允许改变题目顺序（seq 必须严格递增，与原试卷顺序一致）。",
            "- LaTeX 公式必须完整保留，不得截断或转义。",
            "- 严格避免将章节标题（如\"选择题\"、\"填空题\"）作为独立题目输出。",
            "- 【最重要】严禁自行编造、推导或补充答案。只输出试卷文本中明确存在的内容。"
    );

    private final ZhipuAiClient zhipuAiClient;
    private final ExamParseAiProperties properties;

    public ExamSplitLlmClient(ZhipuAiClient zhipuAiClient, ExamParseAiProperties properties) {
        this.zhipuAiClient = zhipuAiClient;
        this.properties = properties;
    }

    /**
     * 调用 GLM-Z-Plus 进行试卷拆题。
     *
     * @param aggregatedOcrText 整合后的 OCR 全文
     * @param hasAnswerHint     用户是否勾选了"此试卷包含答案"
     * @return LLM 原始响应文本
     */
    public String split(String aggregatedOcrText, boolean hasAnswerHint) {
        String hint = hasAnswerHint
                ? "\n[用户提示：此试卷包含答案，请确保将答案与题目一一匹配。]\n"
                : "\n[用户提示：此试卷不包含答案。你绝对不能自行生成、推导或编造任何答案。" +
                  "###ANSWER_START### 到 ###ANSWER_END### 之间必须留空。]\n";

        String userPrompt = hint
                + "\n以下是试卷的 OCR 文本，请按照系统指令格式进行解析：\n\n"
                + aggregatedOcrText;

        log.info("Calling GLM-Z-Plus for exam splitting (model={}, userPrompt_len={}, hasAnswerHint={})",
                properties.getModel(), userPrompt.length(), hasAnswerHint);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String result = doSplit(userPrompt);
            if (result != null && !result.isBlank()) {
                log.info("Exam split LLM returned (len={}, attempt={})", result.length(), attempt);
                return result;
            }
            log.warn("GLM returned empty content for exam split (attempt={}/{})", attempt, MAX_RETRIES);
        }

        throw new RuntimeException("GLM exam split returned empty content after "
                + MAX_RETRIES + " attempts (model=" + properties.getModel() + ")");
    }

    private String doSplit(String userPrompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(properties.getModel())
                .messages(List.of(
                        ChatMessage.builder().role(ChatMessageRole.SYSTEM.value()).content(SYSTEM_PROMPT).build(),
                        ChatMessage.builder().role(ChatMessageRole.USER.value()).content(userPrompt).build()
                ))
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .stream(false)
                .build();

        ChatCompletionResponse response = zhipuAiClient.chat().createChatCompletion(params);
        if (!response.isSuccess()) {
            String detail = response.getMsg() != null ? response.getMsg() : "(no message)";
            log.error("GLM exam split API call failed: msg={}, code={}", detail, response.getCode());
            throw new RuntimeException("GLM exam split failed: " + detail
                    + " (code=" + response.getCode() + ", model=" + properties.getModel() + ")");
        }

        Object content = response.getData().getChoices().get(0).getMessage().getContent();
        return content != null ? content.toString().trim() : "";
    }
}
