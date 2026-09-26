package ru.vsm.backend.auth.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.repository.AppUserRepository;

/**
 * Подготовка для будущего антифрода (см. договорённости проекта): проверка, подтверждена ли
 * личность игрока (сейчас единственный источник — демо-заглушка входа через Госуслуги/ЕСИА,
 * {@code ru.vsm.backend.auth.esia}). На этом шаге результат нигде не используется при начислении
 * очков/ачивок — это отдельная задача домена геймификации.
 */
@Service
@RequiredArgsConstructor
public class PlayerVerificationService {

    private final AppUserRepository appUserRepository;

    /** {@code false} и для незарегистрированного playerId (анонимная игра без учётной записи). */
    public boolean isVerified(UUID playerId) {
        return appUserRepository.findById(playerId).map(AppUser::isVerified).orElse(false);
    }
}
