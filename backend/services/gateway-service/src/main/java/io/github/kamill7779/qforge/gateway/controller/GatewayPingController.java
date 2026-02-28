package io.github.kamill7779.qforge.gateway.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class GatewayPingController {

    @GetMapping("/public/ping")
    public ResponseEntity<Map<String, String>> publicPing() {
        return ResponseEntity.ok(Map.of("message", "gateway ok"));
    }

    @GetMapping("/gateway/ping")
    public ResponseEntity<Map<String, String>> securedPing(
            @RequestHeader(value = "X-Auth-User", defaultValue = "unknown") String username
    ) {
        return ResponseEntity.ok(Map.of("message", "secured gateway ok", "username", username));
    }
}
