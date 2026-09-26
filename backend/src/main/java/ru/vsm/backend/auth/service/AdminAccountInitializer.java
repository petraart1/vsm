package ru.vsm.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.domain.UserRole;
import ru.vsm.backend.auth.repository.AppUserRepository;
import ru.vsm.backend.config.AdminAccountProperties;

/**
 * Создаёт дефолтную учётную запись администратора при старте, если её ещё нет — из
 * {@code app.auth.admin.login}/{@code app.auth.admin.password} (по умолчанию годится только
 * для демо, см. README про смену в проде). Email синтезируется из логина: отдельного свойства
 * для него нет, а {@code AppUser.email} должен быть заполнен и уникален.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAccountInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAccountProperties adminAccountProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String login = adminAccountProperties.getLogin();
        if (appUserRepository.existsByLogin(login)) {
            return;
        }
        AppUser admin = AppUser.builder()
                .login(login)
                .email(login + "@vsm.local")
                .passwordHash(passwordEncoder.encode(adminAccountProperties.getPassword()))
                .displayName("Администратор")
                .role(UserRole.ADMIN)
                .build();
        appUserRepository.save(admin);
        log.info("Создана дефолтная учётная запись администратора (login={})", login);
    }
}
