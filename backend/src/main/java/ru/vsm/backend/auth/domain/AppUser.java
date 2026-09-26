package ru.vsm.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Учётная запись. {@link #id} — тот же {@code playerId}, что используется остальным приложением
 * (заголовок {@code X-Player-Id}, {@code gamification_player_profile.id}, {@code user_progress.player_id}):
 * отдельной FK-связи нет, домены связаны только по id, как и между собой.
 *
 * <p>Публичные игровые эндпоинты пока не требуют учётной записи — совместимость на время
 * поэтапного включения авторизации, см. запись в договорённостях проекта.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 64)
    private String login;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    /**
     * Подтверждённая личность — сейчас выставляется только демо-заглушкой входа через Госуслуги/ЕСИА
     * ({@code ru.vsm.backend.auth.esia}), при обычной регистрации логином/паролем остаётся {@code false}.
     * Подготовка для будущего антифрода, см. {@code PlayerVerificationService.isVerified}.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
