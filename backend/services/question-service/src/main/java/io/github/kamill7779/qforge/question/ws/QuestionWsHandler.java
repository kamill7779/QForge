package io.github.kamill7779.qforge.question.ws;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
        String user = resolveUser(session.getUri());
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

    private String resolveUser(URI uri) {
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

