package ru.vsm.backend.gamification.domain;

/**
 * Статический каталог ачивок MVP. Условия получения оцениваются в
 * {@code GamificationAccrualService} на основе {@code ScenarioCompletedEvent} и уже
 * накопленного состояния профиля/компетенций игрока (после применения очков за текущее
 * событие).
 *
 * <p>Инклюзивная ачивка за обслуживание МГН (идея — {@code dataset/standards/sto-rzd-mgn.md},
 * раздел "Применимость к нашему проекту") сознательно не в этом списке — не первая очередь.
 */
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
    VETERAN("Десять рейсов", "Завершите 10 сценариев", "volume");

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
