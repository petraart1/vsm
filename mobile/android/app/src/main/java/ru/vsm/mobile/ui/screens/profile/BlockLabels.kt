package ru.vsm.mobile.ui.screens.profile

/**
 * Короткие русские названия блоков ситуаций датасета — вместо служебных ключей (lost_found,
 * misc…). Общая мелкая таблица для экранов профиля/наград/главного экрана.
 */
private val BLOCK_TITLES = mapOf(
    "boarding" to "Посадка и проездные документы",
    "baggage" to "Провоз животных и багажа",
    "safety" to "Нарушения порядка и безопасность",
    "seating" to "Место, пересадка, повышение класса",
    "comfort" to "Комфорт и бытовые жалобы",
    "catering" to "Питание и услуги на борту",
    "medical" to "Медицинские и экстренные ситуации",
    "lost_found" to "Вещи, находки, жалобы",
    "conflict" to "Конфликты между пассажирами",
    "misc" to "Прочее / нестандартные запросы",
)

/** Русское название блока ситуаций по его служебному ключу, либо сам ключ, если он неизвестен. */
fun blockLabel(block: String): String = BLOCK_TITLES[block.lowercase()] ?: block

/** Русские подписи причин, по которым сценарий попал в рекомендации. */
fun recommendationReasonLabel(reason: ru.vsm.mobile.domain.model.RecommendationReason): String = when (reason) {
    ru.vsm.mobile.domain.model.RecommendationReason.NOT_PLAYED -> "ещё не пройден"
    ru.vsm.mobile.domain.model.RecommendationReason.FAILED -> "в прошлый раз не удалось"
    ru.vsm.mobile.domain.model.RecommendationReason.PARTIAL -> "пройден частично"
}

/** Русская подпись шага универсальной ролевой модели ответа. */
fun roleStepLabel(step: ru.vsm.mobile.domain.model.RoleStep): String = when (step) {
    ru.vsm.mobile.domain.model.RoleStep.ACKNOWLEDGE -> "Признать"
    ru.vsm.mobile.domain.model.RoleStep.RULE -> "Обозначить правило"
    ru.vsm.mobile.domain.model.RoleStep.SOLUTION -> "Предложить решение"
    ru.vsm.mobile.domain.model.RoleStep.REASSURE -> "Заверить"
}
