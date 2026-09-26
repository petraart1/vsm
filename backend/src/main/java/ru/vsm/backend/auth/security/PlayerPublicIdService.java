package ru.vsm.backend.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vsm.backend.config.PlayerPublicIdProperties;

/**
 * Стабильный необратимый {@code publicId} для публичных ответов (лидерборды игроков) — см.
 * находку CRITICAL в аудите безопасности: реальный {@code playerId} нельзя отдавать анонимному
 * запросу, потому что он же принимается как самодостаточный идентификатор игрока (заголовок
 * {@code X-Player-Id}) на пишущих игровых эндпоинтах без пароля.
 *
 * <p>{@code HMAC-SHA256(playerId)}, первые 16 hex-символов (8 байт) — детерминированно (один и тот
 * же игрок всегда получает один и тот же {@code publicId}, фронт может стабильно отрисовать
 * "закреплённую" строку), но без секретного ключа {@link PlayerPublicIdProperties} не обратимо к
 * исходному UUID. Коллизии на 8 байтах пренебрежимо маловероятны для масштаба лидерборда (топ-N из
 * не более нескольких тысяч игроков на хакатоне).
 */
@Service
@RequiredArgsConstructor
public class PlayerPublicIdService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int PUBLIC_ID_BYTES = 8;

    private final PlayerPublicIdProperties properties;

    public String publicId(UUID playerId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.getPublicIdSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(playerId.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, PUBLIC_ID_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Не удалось вычислить publicId игрока", e);
        }
    }
}
