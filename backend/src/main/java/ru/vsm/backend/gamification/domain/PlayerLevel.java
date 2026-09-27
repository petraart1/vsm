package ru.vsm.backend.gamification.domain;

import java.util.Optional;

/**
 * Уровень игрока по общему счёту {@code PlayerProfile.totalScore} — виден в профиле и в строке
 * лидерборда. Чисто вычисляемая величина, миграции/хранимого поля не требует.
 *
 * <p>Пороги калиброваны по масштабу очков: за одно прохождение сценария — 20-100 базовых очков
 * (см. {@code GamificationAccrualService}) плюс до ~50 за обе шкалы, всего 51 сценарий;
 * дополнительные очки — за экзамен (до 300, {@code ExamAccrualService}) и челленджи месяца.
 */
public enum PlayerLevel {

    TRAINEE(1, "Стажёр", 0),
    JUNIOR_CONDUCTOR(2, "Проводник-стажёр", 300),
    CONDUCTOR(3, "Проводник", 700),
    CONDUCTOR_SECOND_CLASS(4, "Проводник 2 класса", 1300),
    CONDUCTOR_FIRST_CLASS(5, "Проводник 1 класса", 2100),
    SENIOR_CONDUCTOR(6, "Старший проводник", 3100),
    TRAIN_FOREMAN(7, "Бригадир поезда", 4300),
    MENTOR(8, "Наставник", 5700),
    HONORED_MENTOR(9, "Заслуженный наставник", 7500);

    private final int level;
    private final String title;
    private final int minTotalScore;

    PlayerLevel(int level, String title, int minTotalScore) {
        this.level = level;
        this.title = title;
        this.minTotalScore = minTotalScore;
    }

    public int level() {
        return level;
    }

    public String title() {
        return title;
    }

    public int minTotalScore() {
        return minTotalScore;
    }

    /** Следующий уровень, если текущий не максимальный. */
    public Optional<PlayerLevel> next() {
        PlayerLevel[] all = values();
        int idx = ordinal();
        return idx + 1 < all.length ? Optional.of(all[idx + 1]) : Optional.empty();
    }

    /** Уровень, соответствующий данному {@code totalScore} (пороги идут по возрастанию). */
    public static PlayerLevel forScore(int totalScore) {
        PlayerLevel result = TRAINEE;
        for (PlayerLevel candidate : values()) {
            if (totalScore >= candidate.minTotalScore) {
                result = candidate;
            } else {
                break;
            }
        }
        return result;
    }

    /** Прогресс (0-100) от порога текущего уровня до порога следующего; 100 — на максимальном уровне. */
    public static int progressPercent(int totalScore) {
        PlayerLevel current = forScore(totalScore);
        Optional<PlayerLevel> nextLevel = current.next();
        if (nextLevel.isEmpty()) {
            return 100;
        }
        int span = nextLevel.get().minTotalScore - current.minTotalScore;
        if (span <= 0) {
            return 100;
        }
        int progressed = totalScore - current.minTotalScore;
        long percent = Math.round(progressed * 100.0 / span);
        return (int) Math.min(100, Math.max(0, percent));
    }

    /** Очков не хватает до следующего уровня; {@code null} на максимальном уровне. */
    public static Integer pointsToNextLevel(int totalScore) {
        return forScore(totalScore).next()
                .map(nextLevel -> Math.max(0, nextLevel.minTotalScore - totalScore))
                .orElse(null);
    }
}
