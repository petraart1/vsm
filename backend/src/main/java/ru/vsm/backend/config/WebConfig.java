package ru.vsm.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Dev CORS for the {@code /api/**} surface.
 *
 * <p>Only needed when the frontend is served from a different origin than the backend, i.e. the
 * React dev server on {@code :3000} calling the backend on {@code :8080}. When the frontend is
 * served BY this backend (static files under {@code /}, see {@code build.gradle}
 * {@code processResources} and {@code docker-compose.yml}), same-origin requests don't hit CORS
 * at all — this config is a no-op in that case, harmless to keep enabled everywhere.
 *
 * <p>Origins come from {@code app.cors.allowed-origins} ({@link CorsProperties}) so new origins
 * (e.g. a deployed frontend URL) can be added via a property/env var, not a code change.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (corsProperties.getAllowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST")
                .allowedHeaders("X-Player-Id", "Content-Type", "Accept")
                .allowCredentials(false);
    }
}
