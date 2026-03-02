package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量添加答案请求。
 * <p>
 * 支持一次性传入多个答案（兼容 OCR 识别结果和手动输入场景）。
 */
public class BatchCreateAnswerRequest {

    @NotEmpty(message = "answers must not be empty")
    @Valid
    private List<AnswerItem> answers;

    public List<AnswerItem> getAnswers() {
        return answers;
    }

    public void setAnswers(List<AnswerItem> answers) {
        this.answers = answers;
    }

    public static class AnswerItem {

        @jakarta.validation.constraints.NotBlank(message = "latexText must not be blank")
        private String latexText;

        /**
         * 答案来源：MANUAL（手动输入）/ OCR（OCR 识别）。默认 MANUAL。
         */
        private String source;

        public String getLatexText() {
            return latexText;
        }

        public void setLatexText(String latexText) {
            this.latexText = latexText;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }
}
