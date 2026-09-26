package ru.vsm.backend.auth.web;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.auth.service.AuthLoginService;
import ru.vsm.backend.auth.service.AuthRegistrationService;
import ru.vsm.backend.auth.web.dto.LoginRequest;
import ru.vsm.backend.auth.web.dto.LoginResponse;
import ru.vsm.backend.auth.web.dto.RegisterRequest;
import ru.vsm.backend.auth.web.dto.UserProfileResponse;

/** Регистрация, логин (JWT) и текущий профиль по токену. Правила по ролям — шаг 3. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Тот же заголовок, что и у игровых эндпоинтов ({@code ScenarioPlayController.PLAYER_ID_HEADER}) —
     * не импортируется напрямую, чтобы не тянуть зависимость auth -> scenario ради одной константы. */
    private static final String PLAYER_ID_HEADER = "X-Player-Id";

    private final AuthRegistrationService authRegistrationService;
    private final AuthLoginService authLoginService;

    /**
     * Опциональный {@value #PLAYER_ID_HEADER} — id уже накопленного анонимного прогресса
     * (см. javadoc {@code AuthRegistrationService.register}), не обязателен для регистрации "с нуля".
     */
    @PostMapping("/register")
    public ResponseEntity<UserProfileResponse> register(
            @RequestBody RegisterRequest request,
            @RequestHeader(value = PLAYER_ID_HEADER, required = false) String playerIdHeader) {
        UserProfileResponse profile = authRegistrationService.register(request, parseOptionalPlayerId(playerIdHeader));
        return ResponseEntity.status(HttpStatus.CREATED).body(profile);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authLoginService.login(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authLoginService.me(authorization);
    }

    private UUID parseOptionalPlayerId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Заголовок " + PLAYER_ID_HEADER + " должен быть UUID, получено: '" + raw + "'", e);
        }
    }
}
