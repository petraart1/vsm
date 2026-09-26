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

        // auth (регистрация/логин учётной записи, см. AuthRepository)
        /** 401 — неверный логин или пароль (сообщение одинаковое в обоих случаях, см. backend). */
        const val INVALID_CREDENTIALS = "invalid_credentials"

        /** 401 — токен отсутствует/невалиден/просрочен ([ru.vsm.mobile.domain.repository.AuthRepository] не трогает этот код напрямую, для `/me`-подобных вызовов в будущем). */
        const val INVALID_TOKEN = "invalid_token"

        /** 409 — логин уже занят другой учёткой. */
        const val LOGIN_ALREADY_TAKEN = "login_already_taken"

        /** 409 — email уже занят другой учёткой. */
        const val EMAIL_ALREADY_TAKEN = "email_already_taken"

        /** 409 — переданный анонимный playerId уже принадлежит другой учётной записи. */
        const val PLAYER_ALREADY_REGISTERED = "player_already_registered"

        /** 400 — логин короче минимальной длины. */
        const val LOGIN_TOO_SHORT = "login_too_short"

        /** 400 — email не проходит формат. */
        const val EMAIL_INVALID = "email_invalid"

        /** 400 — пароль короче минимальной длины. */
        const val PASSWORD_TOO_SHORT = "password_too_short"

        // exam (режим экзамена, см. ru.vsm.mobile.domain.repository.ExamRepository)
        /** 404 — экзамен с таким id не найден. */
        const val EXAM_NOT_FOUND = "exam_not_found"

        /** 409 — экзамен уже завершён (повторный `startCurrent` поверх готового результата не применим). */
        const val EXAM_ALREADY_FINISHED = "exam_already_finished"

        /**
         * 409 — разбор недоступен, пока экзамен, к которому относится это прохождение, не
         * завершён целиком. Код синтезирован на клиенте: в отличие от остальных ошибок API,
         * backend отвечает на этот конфликт без структурированного тела `{error, message}` (см.
         * `ru.vsm.mobile.data.repository.FeedbackRepositoryImpl.getDebrief`, которая нормализует
         * любой HTTP 409 этого эндпоинта в этот код — для разбора это единственная причина конфликта).
         */
        const val DEBRIEF_UNAVAILABLE_DURING_EXAM = "debrief_unavailable_during_exam"
    }
}

/** true для любого HTTP-статуса 409 (конфликт состояния, а не ошибка запроса). */
fun DomainError.Api.isConflict(): Boolean = statusCode == 409

/** Частный случай 409 — прохождение уже завершено (см. [DomainError.PROGRESS_ALREADY_COMPLETED]). */
fun DomainError.Api.isProgressAlreadyCompleted(): Boolean =
    errorCode == DomainError.PROGRESS_ALREADY_COMPLETED

fun DomainError.Api.isNotFound(): Boolean = statusCode == 404

/** true для HTTP 401 (неверные учётные данные либо невалидный токен — см. [DomainError.INVALID_CREDENTIALS]/[DomainError.INVALID_TOKEN]). */
fun DomainError.Api.isUnauthorized(): Boolean = statusCode == 401

/** Частный случай 409 — разбор недоступен, пока экзамен не завершён (см. [DomainError.DEBRIEF_UNAVAILABLE_DURING_EXAM]). */
fun DomainError.Api.isDebriefUnavailableDuringExam(): Boolean =
    errorCode == DomainError.DEBRIEF_UNAVAILABLE_DURING_EXAM
