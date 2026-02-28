package io.github.kamill7779.qforge.auth.dto;

public record LoginResponse(
        String accessToken,
        long expiresInSeconds,
        String tokenType
) {
}
