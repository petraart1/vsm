package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Секрет для {@code ru.vsm.backend.auth.security.PlayerPublicIdService} — HMAC-ключ, которым */
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class PlayerPublicIdProperties {

    private String publicIdSecret = "vsm-demo-public-id-secret-please-change-in-production-0123456789";
}
