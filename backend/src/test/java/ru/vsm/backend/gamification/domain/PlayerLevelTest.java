package ru.vsm.backend.gamification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Пороги уровней и производные величины (прогресс/очков до следующего) — чистая логика, без Spring. */
class PlayerLevelTest {

    @Test
    void scoreBelowFirstThresholdIsTrainee() {
        assertThat(PlayerLevel.forScore(0)).isEqualTo(PlayerLevel.TRAINEE);
        assertThat(PlayerLevel.forScore(299)).isEqualTo(PlayerLevel.TRAINEE);
    }

    @Test
    void scoreExactlyAtThresholdReachesThatLevel() {
        assertThat(PlayerLevel.forScore(300)).isEqualTo(PlayerLevel.JUNIOR_CONDUCTOR);
        assertThat(PlayerLevel.forScore(700)).isEqualTo(PlayerLevel.CONDUCTOR);
    }

    @Test
    void thresholdsAreStrictlyIncreasing() {
        PlayerLevel[] levels = PlayerLevel.values();
        for (int i = 1; i < levels.length; i++) {
            assertThat(levels[i].minTotalScore()).isGreaterThan(levels[i - 1].minTotalScore());
            assertThat(levels[i].level()).isEqualTo(levels[i - 1].level() + 1);
        }
    }

    @Test
    void nineLevelsDefined() {
        assertThat(PlayerLevel.values()).hasSize(9);
    }

    @Test
    void maxLevelHasNoNextAndFullProgress() {
        PlayerLevel max = PlayerLevel.values()[PlayerLevel.values().length - 1];
        assertThat(max.next()).isEmpty();
        assertThat(PlayerLevel.progressPercent(max.minTotalScore() + 10_000)).isEqualTo(100);
        assertThat(PlayerLevel.pointsToNextLevel(max.minTotalScore() + 10_000)).isNull();
    }

    @Test
    void progressAndPointsToNextLevelAreConsistent() {
        // TRAINEE(0) -> JUNIOR_CONDUCTOR(300): halfway at 150.
        assertThat(PlayerLevel.progressPercent(150)).isEqualTo(50);
        assertThat(PlayerLevel.pointsToNextLevel(150)).isEqualTo(150);

        assertThat(PlayerLevel.progressPercent(0)).isZero();
        assertThat(PlayerLevel.pointsToNextLevel(0)).isEqualTo(300);
    }
}
