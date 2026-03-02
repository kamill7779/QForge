package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新题干请求。客户端发送确认后的 XML 题干文本。
 * <p>
 * 服务端会对 stemXml 执行 XML 规范校验，校验失败返回明确错误信息。
 */
public class UpdateStemRequest {

    @NotBlank(message = "stemXml must not be blank")
    private String stemXml;

    public String getStemXml() {
        return stemXml;
    }

    public void setStemXml(String stemXml) {
        this.stemXml = stemXml;
    }
}
