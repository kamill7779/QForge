package io.github.kamill7779.qforge.ai.controller;

import io.github.kamill7779.qforge.ai.dto.ChatRequest;
import io.github.kamill7779.qforge.ai.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Spring AI 基本用法 Demo Controller
 * <p>
 * 演示三种典型调用模式：
 * <ul>
 *   <li>POST /ai/chat          — 同步调用，返回完整回复</li>
 *   <li>POST /ai/chat/stream   — 流式调用（SSE），逐 token 输出</li>
 *   <li>GET  /ai/chat/simple   — 最简单的一行调用示例</li>
 * </ul>
 */
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatModel chatModel;

    // ----------------------------------------------------------------
    // 1. 同步调用 — ChatClient 流式构建器风格（Spring AI 1.x 推荐写法）
    // ----------------------------------------------------------------

    /**
     * 同步问答：POST /ai/chat
     * <pre>
     * {
     *   "message": "你好，请介绍一下 Spring AI"
     * }
     * </pre>
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String reply = ChatClient.create(chatModel)
                .prompt()
                .user(request.getMessage())
                .call()
                .content();
        return new ChatResponse(reply);
    }

    // ----------------------------------------------------------------
    // 2. 流式调用 (SSE) — POST /ai/chat/stream
    // ----------------------------------------------------------------

    /**
     * 流式问答（Server-Sent Events）：POST /ai/chat/stream
     * curl 示例：
     * <pre>
     * curl -N -X POST http://localhost:8085/ai/chat/stream \
     *      -H "Content-Type: application/json" \
     *      -d '{"message":"写一首关于春天的短诗"}'
     * </pre>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return ChatClient.create(chatModel)
                .prompt()
                .user(request.getMessage())
                .stream()
                .content();
    }

    // ----------------------------------------------------------------
    // 3. 最简快捷调用 — GET /ai/chat/simple?q=...
    // ----------------------------------------------------------------

    /**
     * 最简调用（直接使用底层 ChatModel）：GET /ai/chat/simple?q=你好
     */
    @GetMapping("/simple")
    public String simpleChat(@RequestParam(name = "q", defaultValue = "你好！请用一句话介绍你自己。") String q) {
        Prompt prompt = new Prompt(new UserMessage(q));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
