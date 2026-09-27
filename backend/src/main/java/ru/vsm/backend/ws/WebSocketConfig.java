package ru.vsm.backend.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import ru.vsm.backend.config.CorsProperties;

/** Регистрирует {@link ProgressWebSocketHandler} на {@code /ws/progress/{progressId}} (обычный */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ProgressWebSocketHandler progressWebSocketHandler;
    private final CorsProperties corsProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(progressWebSocketHandler, "/ws/progress/**");
        if (corsProperties.getAllowedOrigins().isEmpty()) {
            registration.setAllowedOriginPatterns("*");
        } else {
            registration.setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(new String[0]));
        }
    }
}
