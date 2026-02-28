package io.github.kamill7779.qforge.auth.controller;

import io.github.kamill7779.qforge.auth.dto.LoginRequest;
import io.github.kamill7779.qforge.auth.dto.LoginResponse;
import io.github.kamill7779.qforge.auth.service.AuthService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(Principal principal) {
        return ResponseEntity.ok(Map.of("username", principal.getName()));
    }
}
