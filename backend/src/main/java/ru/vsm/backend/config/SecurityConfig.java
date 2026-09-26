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

/**
 * Все 3 шага включения авторизации (роли USER/ADMIN, идея из бэклога — админ-кабинет и редактор
 * сценариев только для ADMIN). {@link JwtAuthenticationFilter} (шаг 2) населяет
 * {@code SecurityContext}/подменяет {@code X-Player-Id} при валидном токене.
 *
 * <p>Шаг 3: {@code /api/admin/**} и {@code /api/editor/**} требуют роль ADMIN (валидный JWT с
 * {@code role=ADMIN} — см. {@code JwtAuthenticationFilter}); всё остальное (игровые эндпоинты по
 * {@code X-Player-Id} без логина, {@code /ws/**}, Swagger, actuator, {@code /api/auth/**})
 * по-прежнему {@code permitAll()}, ничего в этой части не меняется. Гейтится свойством
 * {@code app.auth.admin-protection-enabled} (по умолчанию {@code true}) — {@code false}
 * временно возвращает обе группы эндпоинтов к {@code permitAll()}, например для демо до того, как
 * на фронте появится экран логина; независим от {@code app.editor.enabled}, который решает,
 * существует ли редактор вообще (см. {@code EditorScenarioController}), а не кто имеет к нему
 * доступ. 401/403 отдаются той же единой формой {@link ApiError}, что и остальные ошибки API
 * (см. {@code ru.vsm.backend.config.error.GlobalExceptionHandler}), а не HTML/plain text по
 * умолчанию от Spring Security — эти два случая пишутся вручную в тело ответа, а не через
 * {@code @ExceptionHandler}, потому что security-исключения перехватываются
 * {@code ExceptionTranslationFilter} до того, как запрос вообще доходит до DispatcherServlet.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AdminAccountProperties.class, JwtProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] ADMIN_ONLY_PATHS = {"/api/admin/**", "/api/editor/**"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.auth.admin-protection-enabled:true}")
    private boolean adminProtectionEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Нет сессий/cookie-аутентификации — CSRF нечего защищать на REST-поверхности.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(authorize -> {
                    if (adminProtectionEnabled) {
                        authorize.requestMatchers(ADMIN_ONLY_PATHS).hasRole("ADMIN");
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
