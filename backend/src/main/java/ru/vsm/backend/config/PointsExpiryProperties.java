package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры сгорания баллов за неактивность (см. {@code gamification.expiry.service.PointsExpiryService}).
 *
 * <p>Bound from {@code app.gamification.points-expiry.*}.
 */
@ConfigurationProperties(prefix = "app.gamification.points-expiry")
@Getter
@Setter
public class PointsExpiryProperties {

    /**
     * Через сколько дней без реальной активности (см. {@code PlayerProfile#lastActivityAt}) у
     * игрока сгорает часть {@code totalScore}.
     */
    private int inactivityDays = 14;

    /** Какой процент {@code totalScore} сгорает при достижении порога неактивности. */
    private int expiryPercent = 10;

    /**
     * За сколько дней ДО сгорания игроку приходит предупреждение {@code POINTS_EXPIRING}
     * (то есть предупреждение приходит на {@code inactivityDays - warningDays} день неактивности).
     */
    private int warningDays = 3;

    /** Cron-выражение планового запуска {@code PointsExpiryService#runScheduled} — раз в сутки. */
    private String cron = "0 0 3 * * *";
}
