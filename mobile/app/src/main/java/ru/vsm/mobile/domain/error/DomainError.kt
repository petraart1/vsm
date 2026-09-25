package ru.vsm.mobile.domain.error

/**
 * Ошибки, которые может вернуть любой метод репозиториев `domain` в `Result.failure`.
 * Данные читаются через `Result<T>`, поэтому этот тип обязан быть `Throwable`.
 */
sealed class DomainError(override val message: String?, override val cause: Throwable? = null) :
    Exception(message, cause) {

    /** Нет соединения / таймаут / DNS и т.п. — запрос не дошёл до сервера. */
    data class Network(val original: Throwable) : DomainError(original.message, original)

    /**
     * Сервер ответил кодом ошибки (4xx/5xx). [errorCode] — машиночитаемый код из тела ответа
     * (`error` в `{"error": "...", "message": "..."}`), `null` если тело не разобралось.
     * Сравнивать с константами [DomainError.Companion] (например [PROGRESS_ALREADY_COMPLETED]).
     */
    data class Api(
        val statusCode: Int,
        val errorCode: String?,
        val errorMessage: String?,
    ) : DomainError(errorMessage ?: errorCode ?: "HTTP $statusCode")

    /** Непредвиденная ошибка (разбор ответа, программная ошибка) — не сеть и не HTTP-код. */
    data class Unexpected(val original: Throwable) : DomainError(original.message, original)

    companion object {
        // scenario (REST прохождения)
        const val SCENARIO_NOT_FOUND = "scenario_not_found"
        const val PROGRESS_NOT_FOUND = "progress_not_found"
        const val CHOICE_NOT_AVAILABLE = "choice_not_available"
        const val NO_ACTIVE_TIMER = "no_active_timer"
        const val PROGRESS_ACCESS_DENIED = "progress_access_denied"

        /** 409 — прохождение уже завершено, повторный выбор/таймаут не применяется. */
        const val PROGRESS_ALREADY_COMPLETED = "progress_already_completed"
        const val CONCURRENT_MODIFICATION = "concurrent_modification"
        const val INVALID_ARGUMENT = "invalid_argument"
        const val MISSING_HEADER = "missing_header"
    }
}

/** true для любого HTTP-статуса 409 (конфликт состояния, а не ошибка запроса). */
fun DomainError.Api.isConflict(): Boolean = statusCode == 409

/** Частный случай 409 — прохождение уже завершено (см. [DomainError.PROGRESS_ALREADY_COMPLETED]). */
fun DomainError.Api.isProgressAlreadyCompleted(): Boolean =
    errorCode == DomainError.PROGRESS_ALREADY_COMPLETED

fun DomainError.Api.isNotFound(): Boolean = statusCode == 404
