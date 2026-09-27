package ru.vsm.backend.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.vsm.backend.auth.service.exception.TooManyAttemptsException;

/** Простой in-memory лимит попыток входа по паре логин+IP — HIGH из аудита безопасности */
@Component
@Slf4j
public class LoginRateLimiter {

    @Value("${app.auth.login-rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.auth.login-rate-limit.block-duration-seconds:60}")
    private long blockDurationSeconds;

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** Бросает {@link TooManyAttemptsException}, если по этому ключу сейчас действует блокировка. */
    public void checkAllowed(String login, String ip) {
        Attempt attempt = attempts.get(key(login, ip));
        if (attempt != null && attempt.blockedUntil() != null && Instant.now().isBefore(attempt.blockedUntil())) {
            throw new TooManyAttemptsException("too_many_attempts");
        }
    }

    /** Неудачная попытка — увеличивает счётчик, при достижении порога включает блокировку на {@code blockDurationSeconds}. */
    public void recordFailure(String login, String ip) {
        String key = key(login, ip);
        Duration blockDuration = Duration.ofSeconds(blockDurationSeconds);
        attempts.compute(key, (k, current) -> {
            int failures = (current == null ? 0 : current.failures()) + 1;
            Instant blockedUntil = failures >= maxAttempts ? Instant.now().plus(blockDuration) : null;
            if (blockedUntil != null) {
                log.warn("Вход заблокирован на {} после {} неудачных попыток (login={})", blockDuration, failures, login);
            }
            return new Attempt(failures, blockedUntil);
        });
    }

    /** Успешный вход — сбрасывает счётчик для этой пары логин+IP. */
    public void recordSuccess(String login, String ip) {
        attempts.remove(key(login, ip));
    }

    private String key(String login, String ip) {
        return (login == null ? "" : login.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    private record Attempt(int failures, Instant blockedUntil) {
    }
}
