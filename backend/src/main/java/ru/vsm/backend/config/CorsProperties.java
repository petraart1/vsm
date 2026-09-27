package ru.vsm.backend.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dev/deploy-configurable CORS origins for the {@code /api/**} surface. */
@ConfigurationProperties(prefix = "app.cors")
@Getter
@Setter
public class CorsProperties {

    /** Origins allowed to call {@code /api/**} with credentials-less CORS requests. */
    private List<String> allowedOrigins = new ArrayList<>();
}
