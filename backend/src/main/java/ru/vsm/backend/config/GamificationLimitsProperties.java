package ru.vsm.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Антифрод-лимиты начисления очков (см. договорённости проекта по Q&A хакатона: полные очки — */
@ConfigurationProperties(prefix = "app.gamification")
@Getter
@Setter
public class GamificationLimitsProperties {

    /** Максимум очков (после множителя неподтверждённости, но до обрезки лимитом), которые можно */
    private int dailyPointsLimit = 3000;

    /** Множитель очков за одно прохождение для неподтверждённого профиля ({@link */
    private double unverifiedMultiplier = 0.5;
}
