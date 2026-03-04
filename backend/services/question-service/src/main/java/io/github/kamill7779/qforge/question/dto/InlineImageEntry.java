package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 题干中单张内联图片的传输对象。
 * 随 {@link UpdateStemRequest} 一起提交，key 为前端 ref（如 img-1）。
 *
 * <p>约束：
 * <ul>
 *   <li>每题最多 10 张（由 UpdateStemRequest 校验）</li>
 *   <li>每张 imageData base64 解码后 ≤ 524,288 字节（512 KB）</li>
 * </ul>
 */
public record InlineImageEntry(

        /** 图片 base64 编码字符串（不含 data URI 前缀）。 */
        @NotBlank(message = "imageData must not be blank")
        String imageData,

        /** MIME 类型，如 image/png、image/jpeg。为空时默认 image/png。 */
        String mimeType

) {
    /** 最大允许的二进制字节数：512 KB。 */
    public static final int MAX_BINARY_BYTES = 512 * 1024;

    /** base64 字符串长度上限（512KB binary × 4/3 ≈ 699050）。 */
    public static final int MAX_BASE64_LENGTH = 699_050;
}
