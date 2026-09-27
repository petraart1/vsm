package ru.vsm.backend.gamification.expiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.config.PointsExpiryProperties;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.expiry.service.PointsExpiryService;
import ru.vsm.backend.gamification.expiry.web.dto.PointsExpiryRunResult;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * Сгорание баллов за неактивность ({@link PointsExpiryService}): порог неактивности, предупреждение
 * без дублей, само сгорание без дублей при повторном запуске в тот же момент.
 *
 * <p>Дефолты из {@code application.properties} (проверяются, а не хардкодятся, чтобы тест не
 * рассинхронизировался с конфигом): {@code inactivityDays=14}, {@code warningDays=3},
 * {@code expiryPercent=10}.
 */
@SpringBootTest
@Testcontainers
class PointsExpiryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private PointsExpiryService pointsExpiryService;

    @Autowired
    private PointsExpiryProperties properties;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** Известный игрок с заданным счётом и моментом последней реальной активности. */
    private UUID playerWithActivity(int totalScore, Instant lastActivityAt) {
        UUID playerId = UUID.randomUUID();
        eventPublisher.publishEvent(new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "boarding-no-ticket", "boarding",
                ScenarioOutcome.SUCCESS, 0, 0, 1, false, true,
                Instant.now().minusSeconds(30), Instant.now(), false, true));

        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        profile.setTotalScore(totalScore);
        profile.setLastActivityAt(lastActivityAt);
        playerProfileRepository.save(profile);
        return playerId;
    }

    @Test
    void playerBelowWarningThresholdIsUntouched() {
        Instant now = Instant.now();
        UUID playerId = playerWithActivity(1000, now.minus(5, ChronoUnit.DAYS));

        PointsExpiryRunResult result = pointsExpiryService.run(now);

        assertThat(result.warnedPlayers()).isZero();
        assertThat(result.expiredPlayers()).isZero();
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(1000);
        // FIRST_SCENARIO (ачивка за первое прохождение, созданное в playerWithActivity) — не
        // относится к сгоранию баллов, важно лишь отсутствие POINTS_EXPIRING/POINTS_EXPIRED.
        assertThat(notificationService.list(playerId, false)).extracting(NotificationDto::type)
                .noneMatch(type -> type.startsWith("POINTS_EXPIR"));
    }

    @Test
    void playerNearExpiryGetsWarnedOnceEvenAcrossRepeatedRuns() {
        Instant now = Instant.now();
        int warnFromDays = properties.getInactivityDays() - properties.getWarningDays();
        // +60s буфер за пределы ровно "warnFromDays назад" — иначе доли микросекунды, потерянные
        // при округлении timestamp в БД, могут сдвинуть Duration.toDays() на день вниз (граничный
        // случай, которого в реальном суточном планировщике не бывает).
        UUID playerId = playerWithActivity(1000, now.minus(warnFromDays, ChronoUnit.DAYS).minusSeconds(60));

        pointsExpiryService.run(now);
        pointsExpiryService.run(now.plusSeconds(1));

        List<NotificationDto> notifications = notificationService.list(playerId, false);
        assertThat(notifications).extracting(NotificationDto::type)
                .filteredOn(NotificationType.POINTS_EXPIRING.name()::equals)
                .hasSize(1);
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(1000);
    }

    @Test
    void inactivePlayerPastThresholdLosesConfiguredPercentAndIsNotChargedTwice() {
        Instant now = Instant.now();
        // +60s буфер за порог, см. комментарий в playerNearExpiryGetsWarnedOnceEvenAcrossRepeatedRuns.
        UUID playerId = playerWithActivity(1000, now.minus(properties.getInactivityDays(), ChronoUnit.DAYS).minusSeconds(60));

        PointsExpiryRunResult first = pointsExpiryService.run(now);
        assertThat(first.expiredPlayers()).isEqualTo(1);
        int expectedExpired = Math.round(1000 * properties.getExpiryPercent() / 100f);
        assertThat(first.totalPointsExpired()).isEqualTo(expectedExpired);
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore())
                .isEqualTo(1000 - expectedExpired);

        List<NotificationDto> afterFirst = notificationService.list(playerId, false);
        assertThat(afterFirst).extracting(NotificationDto::type)
                .filteredOn(NotificationType.POINTS_EXPIRED.name()::equals)
                .hasSize(1);
        assertThat(afterFirst.get(0).body()).contains(String.valueOf(expectedExpired));

        // Тот же момент (например, второй клик по демо-эндпоинту) не должен сжечь баллы повторно:
        // lastPointsExpiryAt уже сдвинут на now, следующий период неактивности ещё не наступил.
        PointsExpiryRunResult second = pointsExpiryService.run(now);
        assertThat(second.expiredPlayers()).isZero();
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore())
                .isEqualTo(1000 - expectedExpired);
        assertThat(notificationService.list(playerId, false)).extracting(NotificationDto::type)
                .filteredOn(NotificationType.POINTS_EXPIRED.name()::equals)
                .hasSize(1);
    }

    @Test
    void secondExpiryCycleFiresAgainAfterAnotherFullInactivityPeriod() {
        Instant now = Instant.now();
        UUID playerId = playerWithActivity(1000, now.minus(properties.getInactivityDays(), ChronoUnit.DAYS).minusSeconds(60));

        pointsExpiryService.run(now);
        int afterFirstExpiry = playerProfileRepository.findById(playerId).orElseThrow().getTotalScore();

        // +60s буфер за порог, см. комментарий в playerNearExpiryGetsWarnedOnceEvenAcrossRepeatedRuns.
        Instant nextCycle = now.plus(properties.getInactivityDays(), ChronoUnit.DAYS).plusSeconds(60);
        PointsExpiryRunResult second = pointsExpiryService.run(nextCycle);

        assertThat(second.expiredPlayers()).isEqualTo(1);
        int expectedSecondExpiry = Math.round(afterFirstExpiry * properties.getExpiryPercent() / 100f);
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore())
                .isEqualTo(afterFirstExpiry - expectedSecondExpiry);
        assertThat(notificationService.list(playerId, false)).extracting(NotificationDto::type)
                .filteredOn(NotificationType.POINTS_EXPIRED.name()::equals)
                .hasSize(2);
    }

    @Test
    void playerWithZeroScoreIsSkippedEntirely() {
        Instant now = Instant.now();
        UUID playerId = playerWithActivity(0, now.minus(30, ChronoUnit.DAYS));

        PointsExpiryRunResult result = pointsExpiryService.run(now);

        assertThat(result.expiredPlayers()).isZero();
        assertThat(result.warnedPlayers()).isZero();
        assertThat(notificationService.list(playerId, false)).extracting(NotificationDto::type)
                .noneMatch(type -> type.startsWith("POINTS_EXPIR"));
    }
}
