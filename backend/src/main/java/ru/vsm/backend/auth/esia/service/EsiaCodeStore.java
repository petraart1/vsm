package ru.vsm.backend.auth.esia.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory хранилище одноразовых кодов авторизации демо-ЕСИА (аналог короткоживущего
 * {@code authorization_code} из настоящего OAuth2/OIDC). Переживает только время работы процесса —
 * для демо-заглушки этого достаточно, персистентность не нужна.
 */
@Component
public class EsiaCodeStore {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();

    public String issue(UUID citizenId) {
        String code = UUID.randomUUID().toString();
        codes.put(code, new Entry(citizenId, Instant.now().plus(TTL)));
        return code;
    }

    /** Код одноразовый — удаляется при первом же обращении, независимо от результата. */
    public Optional<UUID> consume(String code) {
        Entry entry = codes.remove(code);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.citizenId());
    }

    private record Entry(UUID citizenId, Instant expiresAt) {
    }
}
