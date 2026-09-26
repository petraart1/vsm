package ru.vsm.backend.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.domain.UserRole;
import ru.vsm.backend.config.JwtProperties;

/**
 * Выпуск и проверка JWT (HS256) для логина: {@code subject} — id учётной записи (тот же playerId,
 * что и везде в приложении), claims — {@code role}/{@code login}. Библиотека — jjwt, обоснование
 * выбора см. в договорённостях проекта.
 */
@Component
@Slf4j
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String LOGIN_CLAIM = "login";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(properties.getExpirationMinutes());
    }

    public String issueToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim(LOGIN_CLAIM, user.getLogin())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** Пустой результат — токен отсутствует/просрочен/подделан/битые claims: считаем его невалидным. */
    public Optional<JwtClaims> parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            Claims claims = jws.getPayload();
            UUID playerId = UUID.fromString(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get(ROLE_CLAIM, String.class));
            String login = claims.get(LOGIN_CLAIM, String.class);
            return Optional.of(new JwtClaims(playerId, role, login));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Отклонён невалидный JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** {@code "Bearer <token>"} -> {@code "<token>"}; пусто, если заголовка нет или он не Bearer. */
    public static Optional<String> extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
