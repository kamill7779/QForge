package io.github.kamill7779.qforge.question.client;

import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// url 参数已删除：Feign 通过 Nacos 服务发现 + Spring Cloud LoadBalancer 自动路由，
// 支持 ocr-service 多实例负载均衡。
@FeignClient(name = "ocr-service")
public interface OcrServiceClient {

    @PostMapping("/internal/ocr/tasks")
    OcrTaskAcceptedResponse createTask(@RequestBody OcrServiceCreateTaskRequest request);
}

