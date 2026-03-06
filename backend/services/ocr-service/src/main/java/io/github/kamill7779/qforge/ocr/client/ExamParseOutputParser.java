package io.github.kamill7779.qforge.ocr.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 解析 LLM 输出的试卷拆题结果。
 */
@Component
public class ExamParseOutputParser {

    private static final Logger log = LoggerFactory.getLogger(ExamParseOutputParser.class);

    private static final Pattern QUESTION_BLOCK_PATTERN = Pattern.compile(
            "###QUESTION_START###\\s+seq=(\\d+)(.*?)###QUESTION_END###",
            Pattern.DOTALL);
    private static final Pattern TYPE_PATTERN = Pattern.compile("###TYPE###\\s*(.+)");
    private static final Pattern SOURCE_PAGES_PATTERN = Pattern.compile("###SOURCE_PAGES###\\s*(.+)");
    private static final Pattern STEM_CONTENT_PATTERN = Pattern.compile(
            "###STEM_START###\\s*\\n?(.*?)###STEM_END###", Pattern.DOTALL);
    private static final Pattern STEM_IMAGES_PATTERN = Pattern.compile("###STEM_IMAGES###\\s*(.*)");
    private static final Pattern ANSWER_CONTENT_PATTERN = Pattern.compile(
            "###ANSWER_START###\\s*\\n?(.*?)###ANSWER_END###", Pattern.DOTALL);
    private static final Pattern ANSWER_IMAGES_PATTERN = Pattern.compile("###ANSWER_IMAGES###\\s*(.*)");

    /** 兜底提取：从文本中扫描 image ref */
    private static final Pattern IMAGE_REF_PATTERN = Pattern.compile(
            "<image\\s+ref=\"(fig-[^\"]+)\"[^/]*/>");

    /**
     * 解析后的单题。
     */
    public record ParsedQuestion(
            int seq,
            String questionType,
            String rawStemText,
            List<String> stemImageRefs,
            String rawAnswerText,
            List<String> answerImageRefs,
            List<Integer> sourcePages,
            boolean parseError,
            String errorMsg
    ) {
    }

    /**
     * 解析 LLM 原始输出。
     */
    public List<ParsedQuestion> parse(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            log.warn("LLM output is empty, returning empty result");
            return Collections.emptyList();
        }

        // 尝试提取 ###EXAM_PARSE_START### ... ###EXAM_PARSE_END### 之间内容
        String content = llmOutput;
        int startIdx = llmOutput.indexOf("###EXAM_PARSE_START###");
        int endIdx = llmOutput.indexOf("###EXAM_PARSE_END###");
        if (startIdx >= 0 && endIdx > startIdx) {
            content = llmOutput.substring(startIdx, endIdx + "###EXAM_PARSE_END###".length());
        }

        Matcher questionMatcher = QUESTION_BLOCK_PATTERN.matcher(content);
        List<ParsedQuestion> questions = new ArrayList<>();

        while (questionMatcher.find()) {
            int seq = Integer.parseInt(questionMatcher.group(1));
            String block = questionMatcher.group(2);

            try {
                String type = extractSingleLine(TYPE_PATTERN, block, "UNKNOWN").trim();
                List<Integer> sourcePages = parseSourcePages(
                        extractSingleLine(SOURCE_PAGES_PATTERN, block, ""));
                String stemText = extractBlock(STEM_CONTENT_PATTERN, block);
                List<String> stemImageRefs = parseRefs(
                        extractSingleLine(STEM_IMAGES_PATTERN, block, ""), stemText);
                String answerText = extractBlock(ANSWER_CONTENT_PATTERN, block);
                List<String> answerImageRefs = parseRefs(
                        extractSingleLine(ANSWER_IMAGES_PATTERN, block, ""), answerText);

                questions.add(new ParsedQuestion(
                        seq, type, stemText, stemImageRefs,
                        answerText, answerImageRefs, sourcePages,
                        false, null));
            } catch (Exception ex) {
                log.warn("Failed to parse question seq={}: {}", seq, ex.getMessage());
                questions.add(new ParsedQuestion(
                        seq, "UNKNOWN", block, Collections.emptyList(),
                        null, Collections.emptyList(), Collections.emptyList(),
                        true, ex.getMessage()));
            }
        }

        // 按 seq 排序
        questions.sort((a, b) -> Integer.compare(a.seq(), b.seq()));
        log.info("Parsed {} questions from LLM output", questions.size());
        return questions;
    }

    private String extractSingleLine(Pattern pattern, String block, String defaultValue) {
        Matcher m = pattern.matcher(block);
        return m.find() ? m.group(1).trim() : defaultValue;
    }

    private String extractBlock(Pattern pattern, String block) {
        Matcher m = pattern.matcher(block);
        return m.find() ? m.group(1).trim() : "";
    }

    private List<Integer> parseSourcePages(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    /**
     * 解析 ref 列表。若声明行为空/仅空格，从文本中兜底扫描。
     */
    private List<String> parseRefs(String declared, String textFallback) {
        if (declared != null && !declared.isBlank()) {
            List<String> refs = Arrays.stream(declared.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && s.startsWith("fig-"))
                    .collect(Collectors.toList());
            if (!refs.isEmpty()) return refs;
            // declared line contained no valid refs — fall through to text scan
        }
        // 兜底：从文本中扫描 <image ref="fig-..." />
        if (textFallback != null && !textFallback.isBlank()) {
            List<String> refs = new ArrayList<>();
            Matcher m = IMAGE_REF_PATTERN.matcher(textFallback);
            while (m.find()) {
                refs.add(m.group(1));
            }
            return refs;
        }
        return Collections.emptyList();
    }
}
