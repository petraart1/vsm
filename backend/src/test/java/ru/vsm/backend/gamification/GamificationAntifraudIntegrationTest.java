package ru.vsm.backend.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.auth.domain.AppUser;
import ru.vsm.backend.auth.domain.UserRole;
import ru.vsm.backend.auth.repository.AppUserRepository;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * Антифрод-ограничения {@code GamificationAccrualService.applyAntifraudLimits}: множитель для
 * неподтверждённого профиля и суточный потолок очков на игрока (см. договорённости проекта по
 * Q&amp;A хакатона). Лимит здесь понижен свойством {@code app.gamification.daily-points-limit},
 * чтобы не гонять сотни событий, чтобы его выбить — сам механизм от значения не зависит.
 */
@SpringBootTest(properties = {
        "app.gamification.daily-points-limit=150",
        "app.gamification.unverified-multiplier=0.5"
})
@Testcontainers
class GamificationAntifraudIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    /** base(SUCCESS)=100, никаких дельт по шкалам/таймаута — raw = 100, самое простое число для расчёта. */
    private static ScenarioCompletedEvent plainSuccessEvent(UUID playerId) {
        Instant now = Instant.now();
        return new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "scenario-x", "boarding",
                ScenarioOutcome.SUCCESS, 0, 0, 1, false, false, now.minusSeconds(30), now, false, true);
    }

    private UUID verifiedPlayer() {
        UUID id = UUID.randomUUID();
        appUserRepository.save(AppUser.builder()
                .id(id)
                .login("verified-" + id)
                .email(id + "@example.com")
                .passwordHash("hash")
                .role(UserRole.USER)
                .verified(true)
                .build());
        return id;
    }

    @Test
    void unverifiedPlayerGetsDiscountedPoints() {
        UUID playerId = UUID.randomUUID(); // без AppUser-записи -> isVerified() == false

        eventPublisher.publishEvent(plainSuccessEvent(playerId));

        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        // raw=100, множитель 0.5 -> 50.
        assertThat(profile.getTotalScore()).isEqualTo(50);
    }

    @Test
    void verifiedPlayerGetsFullPoints() {
        UUID playerId = verifiedPlayer();

        eventPublisher.publishEvent(plainSuccessEvent(playerId));

        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        assertThat(profile.getTotalScore()).isEqualTo(100);
    }

    @Test
    void dailyLimitCapsCumulativePointsAcrossMultiplePlaythroughs() {
        UUID playerId = verifiedPlayer(); // подтверждён — изолируем проверку лимита от множителя

        // Лимит=150. Первое прохождение: raw=100, остаток дня 150 -> начислено 100.
        eventPublisher.publishEvent(plainSuccessEvent(playerId));
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(100);

        // Второе прохождение: raw=100, но остаток дня уже только 150-100=50 -> начислено 50, не 100.
        eventPublisher.publishEvent(plainSuccessEvent(playerId));
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(150);

        // Третье прохождение в тот же день: остаток 0 -> очков не добавляется, но прохождение
        // засчитывается (scenariosCompleted растёт) — лимит не блокирует игру, только очки.
        eventPublisher.publishEvent(plainSuccessEvent(playerId));
        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        assertThat(profile.getTotalScore()).isEqualTo(150);
        assertThat(profile.getScenariosCompleted()).isEqualTo(3);
    }
}
