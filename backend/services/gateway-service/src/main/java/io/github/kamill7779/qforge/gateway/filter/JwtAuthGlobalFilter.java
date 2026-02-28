package io.github.kamill7779.qforge.gateway.filter;

import io.github.kamill7779.qforge.gateway.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthGlobalFilter implements WebFilter {

    private final JwtService jwtService;

    @Value("${security.swagger-public:false}")
    private boolean swaggerPublic;

    public JwtAuthGlobalFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String token = authHeader.substring(7);
        if (!jwtService.isValid(token)) {
            return unauthorized(exchange);
        }

        String username = jwtService.extractSubject(token);
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header("X-Auth-User", username)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        if ("/actuator/health".equals(path)) {
            return true;
        }
        if ("/api/auth/login".equals(path) || path.startsWith("/public/") || "/public".equals(path)) {
            return true;
        }
        if (swaggerPublic) {
            return "/swagger-ui.html".equals(path)
                    || path.startsWith("/swagger-ui/")
                    || path.startsWith("/v3/api-docs");
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
