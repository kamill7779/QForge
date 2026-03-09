package io.github.kamill7779.qforge.question.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QuestionWsHandler questionWsHandler;
    private final DynamicOriginHandshakeInterceptor dynamicOriginHandshakeInterceptor;

    public WebSocketConfig(
            QuestionWsHandler questionWsHandler,
            DynamicOriginHandshakeInterceptor dynamicOriginHandshakeInterceptor
    ) {
        this.questionWsHandler = questionWsHandler;
        this.dynamicOriginHandshakeInterceptor = dynamicOriginHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(questionWsHandler, "/ws/questions")
                .addInterceptors(dynamicOriginHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}

