package com.novedadeslz.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novedadeslz.backend.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting por IP para los endpoints anonimos mas sensibles.
 *
 * <p>Deliberadamente en memoria: el backend corre como un unico contenedor en Render, asi que un
 * contador local es suficiente y evita sumar Redis a la infraestructura. Si algun dia se escala a
 * varias instancias, esto hay que moverlo a un store compartido.
 */
@Component
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /** A partir de este tamanno se purgan las ventanas vencidas. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private static final List<Rule> RULES = List.of(
            // Fuerza bruta de credenciales.
            new Rule("login", "POST", "/api/auth/login", 10, Duration.ofMinutes(15)),
            // Enumeracion de pedidos ajenos.
            new Rule("track", "POST", "/api/orders/track", 15, Duration.ofMinutes(15)),
            // Creacion masiva de pedidos basura.
            new Rule("create-order", "POST", "/api/orders", 20, Duration.ofHours(1)),
            // Cada subida consume cuota de OCR, de Cloudinary y del proveedor de WhatsApp.
            new Rule("yape-proof", "POST", "/api/orders/*/yape-proof", 10, Duration.ofHours(1))
    );

    private final ObjectMapper objectMapper;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceCleanup = new AtomicInteger();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Rule rule = findMatchingRule(request);

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = rule.name() + "|" + resolveClientIp(request);

        if (!tryConsume(key, rule)) {
            log.warn("Rate limit alcanzado para {} desde {}", rule.name(), resolveClientIp(request));
            rejectWithTooManyRequests(response, rule);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryConsume(String key, Rule rule) {
        long now = System.currentTimeMillis();
        maybeCleanup(now);

        Window window = windows.compute(key, (ignoredKey, current) -> {
            if (current == null || now - current.windowStart() >= rule.window().toMillis()) {
                return new Window(now, 1);
            }
            return new Window(current.windowStart(), current.count() + 1);
        });

        return window.count() <= rule.maxRequests();
    }

    /**
     * Purga oportunista: sin esto el mapa crece sin limite ante un atacante que rota IPs.
     */
    private void maybeCleanup(long now) {
        if (windows.size() < CLEANUP_THRESHOLD || requestsSinceCleanup.incrementAndGet() < 1_000) {
            return;
        }

        requestsSinceCleanup.set(0);
        long longestWindowMillis = RULES.stream()
                .mapToLong(rule -> rule.window().toMillis())
                .max()
                .orElse(Duration.ofHours(1).toMillis());

        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart() >= longestWindowMillis);
    }

    private Rule findMatchingRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        return RULES.stream()
                .filter(rule -> rule.matches(method, path))
                .findFirst()
                .orElse(null);
    }

    /**
     * Render termina TLS en un proxy, asi que la IP real llega en X-Forwarded-For.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private void rejectWithTooManyRequests(HttpServletResponse response, Rule rule) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(rule.window().toSeconds()));

        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error("Demasiados intentos. Espera unos minutos e intenta de nuevo.")
        );
    }

    private record Window(long windowStart, int count) {
    }

    private record Rule(String name, String method, String pathPattern, int maxRequests, Duration window) {

        boolean matches(String requestMethod, String requestPath) {
            if (!this.method.equalsIgnoreCase(requestMethod)) {
                return false;
            }

            if (!pathPattern.contains("*")) {
                return pathPattern.equals(requestPath);
            }

            // Unico comodin soportado: un segmento variable, como /api/orders/*/yape-proof
            String[] patternSegments = pathPattern.split("/");
            String[] pathSegments = requestPath.split("/");

            if (patternSegments.length != pathSegments.length) {
                return false;
            }

            for (int i = 0; i < patternSegments.length; i++) {
                if (!"*".equals(patternSegments[i]) && !patternSegments[i].equals(pathSegments[i])) {
                    return false;
                }
            }

            return true;
        }
    }
}
