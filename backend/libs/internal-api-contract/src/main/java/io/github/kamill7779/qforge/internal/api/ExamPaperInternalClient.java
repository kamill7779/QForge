package io.github.kamill7779.qforge.internal.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "exam-service")
public interface ExamPaperInternalClient {

    @PostMapping("/internal/exam-papers/from-basket-compose")
    InternalExamPaperDetailDTO createFromBasketCompose(
            @RequestBody BasketComposeCreateExamRequest request,
            @RequestParam("ownerUser") String ownerUser
    );
}
