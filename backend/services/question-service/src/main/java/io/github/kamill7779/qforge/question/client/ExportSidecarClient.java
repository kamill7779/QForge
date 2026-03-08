package io.github.kamill7779.qforge.question.client;

import io.github.kamill7779.qforge.question.dto.export.ExportSidecarRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * export-sidecar Feign 客户端 — 通过 Nacos 服务发现 + LoadBalancer 自动路由。
 *
 * export-sidecar 是纯渲染服务，接收完整题目数据，返回 Word 二进制。
 */
@FeignClient(name = "export-sidecar")
public interface ExportSidecarClient {

    @PostMapping("/internal/export/questions/word")
    feign.Response exportQuestionsWord(@RequestBody ExportSidecarRequest request);
}
