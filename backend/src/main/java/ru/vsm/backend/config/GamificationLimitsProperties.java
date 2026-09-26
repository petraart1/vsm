package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Антифрод-лимиты начисления очков (см. договорённости проекта по Q&A хакатона: полные очки —
 * только подтверждённым личностям, потолок на игрока в сутки против накрутки).
 *
 * <p>Bound from {@code app.gamification.daily-points-limit} /
 * {@code app.gamification.unverified-multiplier} в {@code GamificationAccrualService}.
 */
@ConfigurationProperties(prefix = "app.gamification")
@Getter
@Setter
public class GamificationLimitsProperties {

    /**
     * Максимум очков (после множителя неподтверждённости, но до обрезки лимитом), которые можно
     * начислить одному игроку за календарные сутки (UTC) — сумма {@code totalPointsAwarded} по
     * {@code gamification_accrual_log} за сегодня. Достаточно щедрый дефолт для честной игры (при
     * базовых очках 20-100 за прохождение это ~15-20 прохождений в день), но ограничивает скрипты,
     * гоняющие один и тот же сценарий по кругу.
     */
    private int dailyPointsLimit = 3000;

    /**
     * Множитель очков за одно прохождение для неподтверждённого профиля ({@link
     * ru.vsm.backend.auth.service.PlayerVerificationService#isVerified} == {@code false}) — полные
     * очки только после подтверждения личности (демо-заглушка Госуслуг/ЕСИА,
     * {@code ru.vsm.backend.auth.esia}). Не влияет на анонимную игру без учётной записи по-другому,
     * чем на любой другой неподтверждённый профиль — это единственный сигнал верификации, который
     * есть на хакатоне.
     */
    private double unverifiedMultiplier = 0.5;
}
