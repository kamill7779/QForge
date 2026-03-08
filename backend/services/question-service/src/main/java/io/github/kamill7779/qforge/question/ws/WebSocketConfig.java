package io.github.kamill7779.qforge.question.ws;

import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QuestionWsHandler questionWsHandler;
    private final QForgeBusinessProperties bizProps;

    public WebSocketConfig(QuestionWsHandler questionWsHandler, QForgeBusinessProperties bizProps) {
        this.questionWsHandler = questionWsHandler;
        this.bizProps = bizProps;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(questionWsHandler, "/ws/questions")
                .setAllowedOriginPatterns(bizProps.getWsAllowedOrigins().split(","));
    }
}

