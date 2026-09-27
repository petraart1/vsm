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

/**
 * Человекочитаемое сообщение об ошибке для интерфейса — без HTTP-кодов, технических сообщений
 * исключений и упоминаний внутреннего устройства сервиса. Единственная точка, где [DomainError]
 * превращается в текст, который увидит пользователь; экраны и ViewModel всегда берут сообщение
 * отсюда, а не читают [Throwable.message] напрямую.
 */
fun DomainError.userMessage(): String = when (this) {
    is DomainError.Network -> "Не удалось загрузить данные. Проверьте подключение к интернету и попробуйте ещё раз"
    is DomainError.Unexpected -> "Что-то пошло не так, попробуйте ещё раз"
    is DomainError.Api -> when (errorCode) {
        DomainError.INVALID_CREDENTIALS -> "Неверный логин или пароль"
        DomainError.INVALID_TOKEN -> "Сессия истекла — войдите снова"
        DomainError.LOGIN_ALREADY_TAKEN -> "Этот логин уже занят, выберите другой"
        DomainError.EMAIL_ALREADY_TAKEN -> "Эта почта уже используется другой учётной записью"
        DomainError.PLAYER_ALREADY_REGISTERED -> "Это устройство уже привязано к другой учётной записи"
        DomainError.LOGIN_TOO_SHORT -> "Логин слишком короткий"
        DomainError.EMAIL_INVALID -> "Проверьте правильность почты"
        DomainError.PASSWORD_TOO_SHORT -> "Пароль слишком короткий"
        DomainError.SCENARIO_NOT_FOUND, DomainError.PROGRESS_NOT_FOUND -> "Ситуация недоступна, вернитесь в каталог"
        DomainError.CHOICE_NOT_AVAILABLE -> "Этот вариант ответа уже нельзя выбрать"
        DomainError.NO_ACTIVE_TIMER -> "Время на ответ уже вышло"
        DomainError.PROGRESS_ACCESS_DENIED -> "Это прохождение недоступно для вашей учётной записи"
        DomainError.PROGRESS_ALREADY_COMPLETED -> "Эта ситуация уже пройдена"
        DomainError.CONCURRENT_MODIFICATION -> "Данные обновились в другом месте, попробуйте ещё раз"
        DomainError.EXAM_NOT_FOUND -> "Экзамен недоступен"
        DomainError.EXAM_ALREADY_FINISHED -> "Экзамен уже завершён"
        DomainError.DEBRIEF_UNAVAILABLE_DURING_EXAM -> "Разбор решений появится после завершения экзамена"
        else -> when {
            statusCode == 401 -> "Сессия истекла — войдите снова"
            statusCode == 404 -> "Не удалось найти запрошенные данные"
            statusCode in 500..599 -> "Сервис временно недоступен, попробуйте позже"
            else -> "Не удалось выполнить запрос, попробуйте ещё раз"
        }
    }
}

/** То же самое для любой другой ошибки, попавшей в `Result.failure` вне [DomainError]. */
fun Throwable.userMessage(): String = if (this is DomainError) userMessage() else "Что-то пошло не так, попробуйте ещё раз"
