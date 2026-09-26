package ru.vsm.backend.gamification.team.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * Бригада/депо — единица командного рейтинга. Заполняется идемпотентным сидером
 * ({@code TeamSeeder}) на старте приложения; игрок вступает в команду отдельным действием
 * ({@code TeamService#join}), профиль игрока (см. {@code gamification.domain.PlayerProfile})
 * командой не владеет.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gamification_team")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /** Депо/город приписки бригады (например, "Москва Ленинградская"). */
    @Column(nullable = false, length = 150)
    private String depot;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
