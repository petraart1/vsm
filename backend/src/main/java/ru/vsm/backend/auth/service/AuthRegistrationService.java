package ru.vsm.backend.auth.service;

import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.domain.UserRole;
import ru.vsm.backend.auth.repository.AppUserRepository;
import ru.vsm.backend.auth.service.exception.UserAlreadyExistsException;
import ru.vsm.backend.auth.web.dto.RegisterRequest;
import ru.vsm.backend.auth.web.dto.UserProfileResponse;

/** Регистрация новых учётных записей (роль всегда {@link UserRole#USER} — админ создаётся отдельно). */
@Service
@RequiredArgsConstructor
public class AuthRegistrationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int LOGIN_MIN_LENGTH = 3;
    private static final int PASSWORD_MIN_LENGTH = 8;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * @param anonymousPlayerId необязательный {@code X-Player-Id} анонимной сессии, из которой
     *                          регистрируется игрок — если задан и ещё не занят учёткой, становится
     *                          id новой учётной записи вместо случайного, чтобы уже накопленный
     *                          прогресс/очки (та же id-схема во всех доменах) остались доступны под
     *                          новой учёткой без переноса данных.
     */
    @Transactional
    public UserProfileResponse register(RegisterRequest request, UUID anonymousPlayerId) {
        validate(request);
        String login = request.login().trim();
        String email = request.email().trim();

        if (appUserRepository.existsByLogin(login)) {
            throw new UserAlreadyExistsException("login_already_taken");
        }
        if (appUserRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("email_already_taken");
        }
        if (anonymousPlayerId != null && appUserRepository.existsById(anonymousPlayerId)) {
            throw new UserAlreadyExistsException("player_already_registered");
        }

        AppUser.AppUserBuilder builder = AppUser.builder()
                .login(login)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(blankToNull(request.displayName()))
                .role(UserRole.USER);
        if (anonymousPlayerId != null) {
            builder.id(anonymousPlayerId);
        }
        AppUser saved = appUserRepository.save(builder.build());
        return UserProfileResponse.from(saved);
    }

    private void validate(RegisterRequest request) {
        if (request.login() == null || request.login().trim().length() < LOGIN_MIN_LENGTH) {
            throw new IllegalArgumentException("login_too_short");
        }
        if (request.email() == null || !EMAIL_PATTERN.matcher(request.email().trim()).matches()) {
            throw new IllegalArgumentException("email_invalid");
        }
        if (request.password() == null || request.password().length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("password_too_short");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
