package io.github.kamill7779.qforge.question.ws;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class QuestionWsHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String user = resolveUser(session);
        sessionsByUser.computeIfAbsent(user, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionsByUser.values().forEach(set -> set.remove(session));
    }

    public void sendToUser(String user, String payload) {
        Set<WebSocketSession> sessions = sessionsByUser.get(user);
        if (sessions == null) {
            return;
        }
        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (IOException ignored) {
                    // Ignore transient socket write errors for MVP.
                }
            }
        });
    }

    /**
     * Resolve user identity. Prefer gateway-injected X-Auth-User header (trusted);
     * fall back to URI query param "user" for direct connections.
     */
    private String resolveUser(WebSocketSession session) {
        // 1. Gateway 传递的 header（可信）
        HttpHeaders headers = session.getHandshakeHeaders();
        List<String> authUser = headers.get("X-Auth-User");
        if (authUser != null && !authUser.isEmpty() && !authUser.get(0).isBlank()) {
            return authUser.get(0);
        }
        // 2. Fallback: query param ?user=xxx
        return resolveUserFromUri(session.getUri());
    }

    private String resolveUserFromUri(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return "anonymous";
        }
        for (String pair : uri.getQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "user".equals(parts[0]) && !parts[1].isBlank()) {
                return parts[1];
            }
        }
        return "anonymous";
    }
}

