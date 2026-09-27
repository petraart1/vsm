package ru.vsm.backend.gamification.expiry.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.config.PointsExpiryProperties;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.expiry.web.dto.PointsExpiryRunResult;
import ru.vsm.backend.gamification.repository.NotificationRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.service.NotificationService;

/** Сгорание баллов за длительную неактивность (см. ТЗ: «сгорающие баллы»). */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(PointsExpiryProperties.class)
public class PointsExpiryService {

    private final PlayerProfileRepository playerProfileRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final PointsExpiryProperties properties;

    /** Плановый ежесуточный запуск — см. {@code app.gamification.points-expiry.cron}. */
    @Scheduled(cron = "${app.gamification.points-expiry.cron:0 0 3 * * *}")
    public void runScheduled() {
        PointsExpiryRunResult result = run(Instant.now());
        log.info("Плановое сгорание баллов: предупреждено {}, сгорело у {} игроков ({} баллов суммарно)",
                result.warnedPlayers(), result.expiredPlayers(), result.totalPointsExpired());
    }

    /** @param now момент, относительно которого считается неактивность — {@code Instant.now()} в */
    @Transactional
    public PointsExpiryRunResult run(Instant now) {
        int inactivityDays = properties.getInactivityDays();
        int warningDays = Math.max(0, Math.min(properties.getWarningDays(), inactivityDays));
        int warnFromDays = inactivityDays - warningDays;
        int expiryPercent = properties.getExpiryPercent();

        int warnedPlayers = 0;
        int expiredPlayers = 0;
        int totalPointsExpired = 0;

        for (PlayerProfile profile : playerProfileRepository.findAll()) {
            if (profile.getTotalScore() <= 0) {
                continue;
            }
            Instant reference = referencePoint(profile);
            long daysSinceReference = Duration.between(reference, now).toDays();

            if (daysSinceReference >= inactivityDays) {
                int expiredPoints = Math.round(profile.getTotalScore() * expiryPercent / 100f);
                profile.setLastPointsExpiryAt(now);
                if (expiredPoints > 0) {
                    profile.setTotalScore(profile.getTotalScore() - expiredPoints);
                    profile.setUpdatedAt(now);
                    playerProfileRepository.save(profile);
                    notificationService.notify(profile.getId(), NotificationType.POINTS_EXPIRED,
                            "Баллы сгорели за неактивность",
                            "Сгорело %d баллов из-за отсутствия активности более %d дней"
                                    .formatted(expiredPoints, inactivityDays));
                    expiredPlayers++;
                    totalPointsExpired += expiredPoints;
                } else {
                    playerProfileRepository.save(profile);
                }
            } else if (daysSinceReference >= warnFromDays) {
                boolean alreadyWarned = notificationRepository
                        .existsByPlayerIdAndTypeAndCreatedAtGreaterThanEqual(
                                profile.getId(), NotificationType.POINTS_EXPIRING, reference);
                if (!alreadyWarned) {
                    long daysLeft = inactivityDays - daysSinceReference;
                    notificationService.notify(profile.getId(), NotificationType.POINTS_EXPIRING,
                            "Баллы скоро сгорят",
                            "Через %d дн. без активности сгорит %d%% накопленных баллов"
                                    .formatted(daysLeft, expiryPercent));
                    warnedPlayers++;
                }
            }
        }
        return new PointsExpiryRunResult(warnedPlayers, expiredPlayers, totalPointsExpired);
    }

    /** См. javadoc класса — точка отсчёта текущего периода неактивности этого игрока. */
    private Instant referencePoint(PlayerProfile profile) {
        Instant lastActivity = profile.getLastActivityAt();
        Instant lastExpiry = profile.getLastPointsExpiryAt();
        return lastExpiry != null && lastExpiry.isAfter(lastActivity) ? lastExpiry : lastActivity;
    }
}
