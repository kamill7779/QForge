package io.github.kamill7779.qforge.question.controller;

/**
 * OCR 任务控制器。
 * <p>
 * 原有 /api/ocr-tasks/{taskUuid}/confirmations 接口已移除。
 * OCR 任务确认现在是服务端内部自动管理的操作：
 * - 客户端通过 WS 接收 OCR 结果
 * - 客户端通过 PUT /api/questions/{uuid}/stem 提交最终题干文本
 * - 服务端内部自动将关联 OCR 任务标记为 CONFIRMED
 * <p>
 * 此类保留为占位，后续可扩展 OCR 任务查询等只读接口。
 */
// @RestController
// @RequestMapping("/api/ocr-tasks")
public class OcrTaskController {
    // Intentionally empty after removing the confirm endpoint.
    // OCR task lifecycle is now managed internally by the server.
}

