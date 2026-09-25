package ru.vsm.backend.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dev/deploy-configurable CORS origins for the {@code /api/**} surface.
 *
 * <p>Bound from {@code app.cors.allowed-origins} (comma-separated list, relaxed binding) in
 * {@code application.properties}. Kept as a property rather than a hardcoded origin list so the
 * dev frontend port (3000, since 8080 is taken by the backend) or a future deployed frontend
 * origin can be added without touching Java code — see {@link WebConfig}.
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** Origins allowed to call {@code /api/**} with credentials-less CORS requests. */
    private List<String> allowedOrigins = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
