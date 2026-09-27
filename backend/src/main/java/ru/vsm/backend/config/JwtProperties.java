package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Секрет и срок жизни JWT, выпускаемых при логине (см. {@code auth.security.JwtService}). */
@ConfigurationProperties(prefix = "app.auth.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Демо-дефолт, используется, только если {@code APP_AUTH_JWT_SECRET} не задан — см. {@code JwtService}. */
    public static final String DEFAULT_SECRET = "vsm-demo-jwt-secret-please-change-in-production-0123456789abcdef";

    private String secret = DEFAULT_SECRET;

    private long expirationMinutes = 1440;
}
