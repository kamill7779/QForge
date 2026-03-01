package io.github.kamill7779.qforge.question.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QuestionWsHandler questionWsHandler;

    public WebSocketConfig(QuestionWsHandler questionWsHandler) {
        this.questionWsHandler = questionWsHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(questionWsHandler, "/ws/questions").setAllowedOriginPatterns("*");
    }
}

