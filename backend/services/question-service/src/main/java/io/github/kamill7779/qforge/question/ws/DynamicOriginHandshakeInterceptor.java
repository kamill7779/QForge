package io.github.kamill7779.qforge.question.ws;

import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class DynamicOriginHandshakeInterceptor implements HandshakeInterceptor {

    private final QForgeBusinessProperties businessProperties;

    public DynamicOriginHandshakeInterceptor(QForgeBusinessProperties businessProperties) {
        this.businessProperties = businessProperties;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            java.util.Map<String, Object> attributes
    ) {
        String origin = request.getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            return true;
        }

        for (String pattern : resolveAllowedOrigins()) {
            if (PatternMatchUtils.simpleMatch(pattern, origin)) {
                return true;
            }
        }

        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private List<String> resolveAllowedOrigins() {
        String raw = businessProperties.getWsAllowedOrigins();
        if (raw == null || raw.isBlank()) {
            return List.of("*");
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
