package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 更新题干请求。客户端一次性提交题干 XML + 全部内联图片。
 *
 * <p>约束：
 * <ul>
 *   <li>inlineImages key 为前端 ref（如 img-1），最多 10 个
 *   <li>每张图片 base64 解码后 ≤ 30 KB，由服务端校验
 * </ul>
 */
public class UpdateStemRequest {

    @NotBlank(message = "stemXml must not be blank")
    private String stemXml;

    /**
     * 内联图片 Map，key = 前端 XML ref（如 img-1），每题最多 10 张。
     * 为 null 或空时表示本次提交不含任何配图（会清除已有图片）。
     */
    @Size(max = 10, message = "A question can have at most 10 inline images")
    private Map<String, InlineImageEntry> inlineImages;

    public String getStemXml() {
        return stemXml;
    }

    public void setStemXml(String stemXml) {
        this.stemXml = stemXml;
    }

    public Map<String, InlineImageEntry> getInlineImages() {
        return inlineImages;
    }

    public void setInlineImages(Map<String, InlineImageEntry> inlineImages) {
        this.inlineImages = inlineImages;
    }
}
