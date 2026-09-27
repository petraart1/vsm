package ru.vsm.mobile.domain.model

/**
 * Награда, выданная администратором вручную (не автоматической ачивкой) — каталог наравне с
 * [Achievement], но с собственной формой/значком для витрины. [earned]/[earnedAt] заполнены
 * только для каталога конкретного игрока (см.
 * [ru.vsm.mobile.domain.repository.GamificationRepository.getCustomAwards]).
 */
data class CustomAward(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val shape: String,
    val glyph: String?,
    val verifiedOnly: Boolean,
    val earned: Boolean,
    val earnedAt: String?,
)
