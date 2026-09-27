package ru.vsm.mobile.domain.model

/** Одна награда в витрине игрока — не обязательно ачивка/кастомная награда 1:1, [id] см. javadoc backend. */
data class ShowcaseItem(
    val id: String,
    val title: String,
    val shape: String,
    val glyph: String?,
    val text: String?,
)

/** Витрина игрока для показа коллегам: отделка медалей и награды в порядке, выбранном владельцем. */
data class Showcase(
    val finish: String,
    val items: List<ShowcaseItem>,
)
