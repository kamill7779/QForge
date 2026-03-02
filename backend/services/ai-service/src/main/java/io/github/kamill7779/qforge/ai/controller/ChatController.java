package io.github.kamill7779.qforge.ai.controller;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;
import io.github.kamill7779.qforge.ai.config.ZhipuProperties;
import io.github.kamill7779.qforge.ai.dto.ChatRequest;
import io.github.kamill7779.qforge.ai.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ZhipuAI 官方 SDK Demo Controller
 * <p>
 * API 不变，内部改用 zai-sdk 直连智谱平台：
 * <ul>
 *   <li>POST /ai/chat          — 同步调用，返回完整回复</li>
 *   <li>POST /ai/chat/stream   — 流式调用（SSE），逐 token 输出</li>
 *   <li>GET  /ai/chat/simple   — 最简一行调用，便于快速验证</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ZhipuAiClient zhipuAiClient;
    private final ZhipuProperties props;

    // ----------------------------------------------------------------
    // 公共辅助：构建单轮 ChatMessage 列表
    // ----------------------------------------------------------------
    private ChatCompletionCreateParams buildParams(String userContent, boolean stream) {
        return ChatCompletionCreateParams.builder()
                .model(props.getModel())
                .messages(Collections.singletonList(
                        ChatMessage.builder()
                                .role(ChatMessageRole.USER.value())
                                .content(userContent)
                                .build()
                ))
                .temperature(props.getTemperature())
                .maxTokens(props.getMaxTokens())
                .stream(stream)
                .build();
    }

    // ----------------------------------------------------------------
    // 1. 同步调用 — POST /ai/chat
    // ----------------------------------------------------------------

    /**
     * 同步问答
     * <pre>
     * curl -X POST http://localhost:8085/ai/chat \
     *      -H "Content-Type: application/json" \
     *      -d '{"message":"你好，请介绍一下自己"}'
     * </pre>
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        ChatCompletionCreateParams params = buildParams(request.getMessage(), false);
        ChatCompletionResponse response = zhipuAiClient.chat().createChatCompletion(params);

        if (!response.isSuccess()) {
            log.error("ZhipuAI error: {}", response.getMsg());
            throw new RuntimeException("AI 调用失败: " + response.getMsg());
        }

        Object content = response.getData().getChoices().get(0).getMessage().getContent();
        return new ChatResponse(content != null ? content.toString() : "");
    }

    // ----------------------------------------------------------------
    // 2. 流式调用（SSE）— POST /ai/chat/stream
    // ----------------------------------------------------------------

    /**
     * 流式问答，逐 token 推送 Server-Sent Events
     * <pre>
     * curl -N -X POST http://localhost:8085/ai/chat/stream \
     *      -H "Content-Type: application/json" \
     *      -d '{"message":"写一首关于春天的短诗"}'
     * </pre>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(props.getTimeout().longValue() * 2);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                ChatCompletionCreateParams params = buildParams(request.getMessage(), true);
                ChatCompletionResponse response = zhipuAiClient.chat().createChatCompletion(params);

                if (!response.isSuccess() || response.getFlowable() == null) {
                    emitter.completeWithError(new RuntimeException("Stream unavailable: " + response.getMsg()));
                    return;
                }

                response.getFlowable().blockingSubscribe(
                        chunk -> {
                            if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                                var delta = chunk.getChoices().get(0).getDelta();
                                if (delta != null && delta.getContent() != null) {
                                    emitter.send(delta.getContent().toString());
                                }
                            }
                        },
                        error -> {
                            log.error("Stream error", error);
                            emitter.completeWithError(error);
                        },
                        emitter::complete
                );
            } catch (Exception e) {
                log.error("Stream setup error", e);
                emitter.completeWithError(e);
            }
        });
        executor.shutdown();

        return emitter;
    }

    // ----------------------------------------------------------------
    // 3. 最简快捷调用 — GET /ai/chat/simple?q=...
    // ----------------------------------------------------------------

    /**
     * 最简调用，直接传 q 参数
     * <pre>
     * curl "http://localhost:8085/ai/chat/simple?q=你好"
     * </pre>
     */
    @GetMapping("/simple")
    public String simpleChat(@RequestParam(name = "q", defaultValue = "请用一句话介绍你自己。") String q) {
        ChatCompletionCreateParams params = buildParams(q, false);
        ChatCompletionResponse response = zhipuAiClient.chat().createChatCompletion(params);

        if (!response.isSuccess()) {
            throw new RuntimeException("AI 调用失败: " + response.getMsg());
        }

        Object content = response.getData().getChoices().get(0).getMessage().getContent();
        return content != null ? content.toString() : "";
    }
}
