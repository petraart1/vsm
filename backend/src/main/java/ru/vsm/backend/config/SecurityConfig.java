package ru.vsm.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.vsm.backend.auth.security.JwtAuthenticationFilter;
import ru.vsm.backend.config.error.ApiError;
import tools.jackson.databind.ObjectMapper;

/** Конфигурирует HTTP Security: роли USER/ADMIN, авторизация по JWT. */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AdminAccountProperties.class, JwtProperties.class, PlayerPublicIdProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] ADMIN_ONLY_PATHS = {"/api/admin/**", "/api/editor/**"};
    private static final String[] GAME_PATHS = {
            "/api/scenarios/**", "/api/exams/**",
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.auth.admin-protection-enabled:true}")
    private boolean adminProtectionEnabled;

    @Value("${app.auth.require-token:false}")
    private boolean requireToken;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Нет сессий/cookie-аутентификации — CSRF нечего защищать на REST-поверхности.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(authorize -> {
                    if (adminProtectionEnabled) {
                        authorize.requestMatchers(ADMIN_ONLY_PATHS).hasRole("ADMIN");
                    }
                    if (requireToken) {
                        authorize.requestMatchers(GAME_PATHS).authenticated();
                    }
                    authorize.anyRequest().permitAll();
                })
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, authException) -> writeJsonError(
                                request, response, HttpStatus.UNAUTHORIZED, "unauthorized",
                                "Требуется аутентификация: заголовок Authorization: Bearer <token>"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeJsonError(
                                request, response, HttpStatus.FORBIDDEN, "access_denied", "Требуется роль ADMIN")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeJsonError(
            HttpServletRequest request, HttpServletResponse response, HttpStatus status, String error, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(error, message, request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
