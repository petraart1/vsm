package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Секрет для {@code ru.vsm.backend.auth.security.PlayerPublicIdService} — HMAC-ключ, которым
 * настоящий {@code playerId} превращается в непредсказуемый {@code publicId} для публичных
 * ответов (лидерборды: см. находку CRITICAL в аудите безопасности — реальные {@code playerId}
 * нельзя раскрывать анонимно, иначе они принимаются как есть в заголовке {@code X-Player-Id} на
 * пишущих игровых эндпоинтах).
 *
 * <p>Bound from {@code app.security.public-id-secret}. Дефолт годится только для демо — как и
 * {@link JwtProperties}, для боевого окружения сменить через переменную окружения
 * {@code APP_SECURITY_PUBLIC_ID_SECRET} (см. README).
 */
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class PlayerPublicIdProperties {

    private String publicIdSecret = "vsm-demo-public-id-secret-please-change-in-production-0123456789";
}
