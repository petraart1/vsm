package ru.vsm.mobile.domain.repository

import ru.vsm.mobile.domain.model.CompetencyAnalytics
import ru.vsm.mobile.domain.model.Debrief

/** Обучающая обратная связь: разбор одного прохождения и агрегированная аналитика компетенций. */
interface FeedbackRepository {

    /** Разбор одного завершённого (или прерванного) прохождения сценария. */
    suspend fun getDebrief(userProgressId: String): Result<Debrief>

    /**
     * Аналитика компетенций игрока по всем завершённым прохождениям. Игрок без прохождений
     * получает пустой агрегат ([CompetencyAnalytics.totalPlaythroughs] == 0), не ошибку.
     */
    suspend fun getCompetencies(playerId: String): Result<CompetencyAnalytics>
}
