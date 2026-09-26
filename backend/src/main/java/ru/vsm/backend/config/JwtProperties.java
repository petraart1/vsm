package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Секрет и срок жизни JWT, выпускаемых при логине (см. {@code auth.security.JwtService}).
 *
 * <p>Bound from {@code app.auth.jwt.secret}/{@code app.auth.jwt.expiration-minutes}. Дефолтный
 * секрет годится только для демо — для боевого окружения сменить через переменную окружения
 * {@code APP_AUTH_JWT_SECRET} (см. README). HS256 требует секрет длиной не меньше 32 байт.
 */
@ConfigurationProperties(prefix = "app.auth.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret = "vsm-demo-jwt-secret-please-change-in-production-0123456789abcdef";

    private long expirationMinutes = 1440;
}
