package ru.vsm.mobile.domain.model

/**
 * Результат [ru.vsm.mobile.domain.repository.ScenarioRepository.chooseOrQueue]/[ru.vsm.mobile.domain.repository.ScenarioRepository.timeoutOrQueue] —
 * офлайн-устойчивых вариантов `choose`/`timeout` (см. соединение в поезде, которое может пропадать
 * в разгар прохождения). В отличие от обычных `choose`/`timeout`, эти методы не возвращают
 * `Result.failure(DomainError.Network)` — сетевая ошибка вместо этого откладывает действие в
 * локальную очередь и возвращает успех с [QueuedOffline], чтобы экран прохождения мог продолжить
 * игру не дожидаясь связи.
 */
sealed class ChoiceOutcome {

    /** Выбор применён сервером немедленно — обычный путь при наличии связи. */
    data class Applied(val result: ChoiceResult) : ChoiceOutcome()

    /**
     * Сеть недоступна — действие сохранено в очередь и будет отправлено при восстановлении связи
     * (см. [ru.vsm.mobile.domain.repository.ScenarioRepository.pendingOfflineCount]). [choiceId] —
     * `null`, если отложено действие "время вышло" ([ru.vsm.mobile.domain.repository.ScenarioRepository.timeoutOrQueue]).
     * [queuedAt] — момент постановки в очередь (ISO-8601, время устройства).
     *
     * UI не может показать реальные новые значения шкал/следующий узел до фактической отправки —
     * рекомендуемое поведение: показать индикатор "принято офлайн, будет применено при связи" и
     * держать экран прохождения заблокированным на текущем узле до следующего успешного
     * [ChoiceOutcome.Applied] или обновления через [ru.vsm.mobile.domain.repository.ScenarioRepository.getProgress].
     */
    data class QueuedOffline(
        val progressId: String,
        val choiceId: String?,
        val queuedAt: String,
    ) : ChoiceOutcome()
}
