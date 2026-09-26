package ru.vsm.backend.auth.esia.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.domain.UserRole;
import ru.vsm.backend.auth.esia.domain.EsiaTestCitizen;
import ru.vsm.backend.auth.esia.service.exception.InvalidEsiaCodeException;
import ru.vsm.backend.auth.repository.AppUserRepository;
import ru.vsm.backend.auth.security.JwtService;
import ru.vsm.backend.auth.web.dto.LoginResponse;
import ru.vsm.backend.auth.web.dto.UserProfileResponse;

/**
 * Демо-заглушка входа через Госуслуги/ЕСИА: реальная интеграция за время хакатона невозможна
 * (регистрация информационной системы в ЕСИА, сертификаты ГОСТ Р 34.10, соглашение с Минцифры,
 * доступ к тестовой среде — недели согласований). Вместо этого — фиктивный provider внутри backend
 * с фиксированным набором тестовых граждан: страница выбора вместо формы логина Госуслуг, тот же
 * code-обмен, что и в настоящем OAuth2/OIDC, в конце — обычный JWT, как при обычном логине.
 *
 * <p>Личность гражданина в этом сценарии считается подтверждённой ЕСИА по построению демо (в
 * реальной интеграции это подтверждает сама ЕСИА через scope {@code snils}/{@code fullname}) —
 * поэтому найденная/созданная учётная запись всегда помечается {@code verified=true}.
 */
@Service
@RequiredArgsConstructor
public class EsiaMockService {

    /** Фиксированные id — повторный вход тем же гражданином находит ту же учётную запись, а не плодит новые. */
    public static final List<EsiaTestCitizen> TEST_CITIZENS = List.of(
            new EsiaTestCitizen(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "ivanova", "Иванова Мария Сергеевна", "112-233-445 95"),
            new EsiaTestCitizen(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "petrov", "Петров Алексей Викторович", "223-344-556 06"),
            new EsiaTestCitizen(
                    UUID.fromString("00000000-0000-0000-0000-000000000003"),
                    "sidorova", "Сидорова Ольга Дмитриевна", "334-455-667 17"),
            new EsiaTestCitizen(
                    UUID.fromString("00000000-0000-0000-0000-000000000004"),
                    "kuznetsov", "Кузнецов Артём Игоревич", "445-566-778 28"));

    private static final String EMAIL_DOMAIN = "esia.mock.local";

    private final EsiaCodeStore codeStore;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public String renderCitizenSelectionPage(String redirectUri) {
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        StringBuilder items = new StringBuilder();
        for (EsiaTestCitizen citizen : TEST_CITIZENS) {
            items.append("""
                    <li>
                      <a class="citizen" href="/api/auth/esia/select?redirect_uri=%s&citizen=%s">
                        <strong>%s</strong><br>СНИЛС: %s
                      </a>
                    </li>
                    """.formatted(encodedRedirect, escape(citizen.code()), escape(citizen.fullName()),
                    escape(citizen.snilsMasked())));
        }
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                  <meta charset="UTF-8">
                  <title>Госуслуги (демо)</title>
                  <style>
                    body { font-family: sans-serif; max-width: 480px; margin: 40px auto; padding: 0 16px; color: #222; }
                    .notice { background: #fff3cd; border: 1px solid #ffe08a; padding: 8px 12px; border-radius: 6px; font-size: 14px; }
                    ul { list-style: none; padding: 0; }
                    .citizen { display: block; padding: 12px; margin: 8px 0; border: 1px solid #ccc; border-radius: 8px;
                               text-decoration: none; color: #222; }
                    .citizen:hover { background: #f4f4f4; }
                  </style>
                </head>
                <body>
                  <h1>Вход через Госуслуги (демо)</h1>
                  <p class="notice">Демонстрационная заглушка для хакатона. Не является настоящим порталом Госуслуг
                    и не проверяет реальные учётные записи ЕСИА.</p>
                  <p>Выберите тестового гражданина для входа:</p>
                  <ul>
                    %s
                  </ul>
                </body>
                </html>
                """.formatted(items);
    }

    /** @throws IllegalArgumentException неизвестный код тестового гражданина -> 400 */
    public String issueAuthorizationCode(String citizenCode) {
        EsiaTestCitizen citizen = findByCode(citizenCode)
                .orElseThrow(() -> new IllegalArgumentException("unknown_test_citizen"));
        return codeStore.issue(citizen.id());
    }

    @Transactional
    public LoginResponse exchangeCode(String code) {
        UUID citizenId = codeStore.consume(code)
                .orElseThrow(() -> new InvalidEsiaCodeException("invalid_or_expired_esia_code"));
        EsiaTestCitizen citizen = TEST_CITIZENS.stream()
                .filter(c -> c.id().equals(citizenId))
                .findFirst()
                .orElseThrow(() -> new InvalidEsiaCodeException("invalid_or_expired_esia_code"));

        AppUser user = appUserRepository.findById(citizenId).orElseGet(() -> createUser(citizen));
        if (!user.isVerified()) {
            user.setVerified(true);
            user = appUserRepository.save(user);
        }

        String token = jwtService.issueToken(user);
        return new LoginResponse(token, UserProfileResponse.from(user));
    }

    private AppUser createUser(EsiaTestCitizen citizen) {
        AppUser user = AppUser.builder()
                .id(citizen.id())
                .login("esia_" + citizen.code())
                .email(citizen.code() + "@" + EMAIL_DOMAIN)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .displayName(citizen.fullName())
                .role(UserRole.USER)
                .verified(true)
                .build();
        return appUserRepository.save(user);
    }

    private Optional<EsiaTestCitizen> findByCode(String citizenCode) {
        return TEST_CITIZENS.stream().filter(c -> c.code().equals(citizenCode)).findFirst();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
