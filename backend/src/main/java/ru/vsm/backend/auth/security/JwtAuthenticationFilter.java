package ru.vsm.backend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Шаг 2 включения авторизации: если запрос несёт {@code Authorization: Bearer <jwt>} и токен
 * валиден, playerId для всего остального конвейера (REST-контроллеры, дальше по цепочке) берётся
 * из токена, а не из клиентского заголовка {@code X-Player-Id} — см.
 * {@link PlayerIdOverridingRequestWrapper}. Так существующие контроллеры доменов (scenario и
 * др.), читающие {@code X-Player-Id} напрямую, не нужно менять.
 *
 * <p>Без токена (или с невалидным/просроченным) запрос идёт как раньше — по заголовку
 * {@code X-Player-Id} без аутентификации (анонимный игрок). Авторизация по ролям (доступ к
 * {@code /api/editor/**} и т.п.) появится на шаге 3 — сейчас {@code SecurityFilterChain} остаётся
 * {@code permitAll()}, аутентификация здесь нужна только для {@code /api/auth/me} и на будущее.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<String> token = JwtService.extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        Optional<JwtClaims> claims = token.flatMap(jwtService::parse);

        if (claims.isPresent()) {
            JwtClaims c = claims.get();
            var authentication = new UsernamePasswordAuthenticationToken(
                    c.playerId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + c.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request = new PlayerIdOverridingRequestWrapper(request, c.playerId().toString());
        }

        filterChain.doFilter(request, response);
    }
}
