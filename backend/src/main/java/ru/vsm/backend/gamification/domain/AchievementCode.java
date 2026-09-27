package ru.vsm.backend.gamification.domain;

/** Статический каталог ачивок MVP. Условия получения оцениваются в */
public enum AchievementCode {

    /** Первое завершённое прохождение любого сценария. */
    FIRST_SCENARIO("Первый рейс", "Завершите свой первый сценарий", "milestone"),

    /** Успешное завершение с высоким итогом по шкале безопасности. */
    FLAWLESS_SAFETY("Безупречная безопасность",
            "Завершите сценарий с итоговым рейтингом безопасности не ниже 25 и вердиктом «успех»",
            "style"),

    /** Успешное завершение с высоким итогом по шкале лояльности. */
    PASSENGER_FAVORITE("Любимец пассажиров",
            "Завершите сценарий с итоговой лояльностью пассажира не ниже 25 и вердиктом «успех»",
            "style"),

    /** Пройдены сценарии из трёх разных блоков ситуаций. */
    VERSATILE("Универсал", "Пройдите сценарии из трёх разных блоков ситуаций", "volume"),

    /** Десять завершённых прохождений. */
    VETERAN("Десять рейсов", "Завершите 10 сценариев", "volume"),

    /** Выполнен хотя бы один челлендж месяца (см. {@code gamification.challenge}). */
    CHALLENGE_CHAMPION("Чемпион месяца", "Выполните любой челлендж месяца", "challenge"),

    /** Экзамен ({@code ru.vsm.backend.scenario.service.ExamService}) сдан на оценку "отлично" */
    CERTIFICATE("Сертификат", "Сдайте экзамен на оценку «отлично»", "milestone");

    private final String title;
    private final String description;
    private final String category;

    AchievementCode(String title, String description, String category) {
        this.title = title;
        this.description = description;
        this.category = category;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** Категория для группировки на экране "Ачивки" (design/screens/achievements.md). */
    public String category() {
        return category;
    }
}
