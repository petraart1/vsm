package ru.vsm.mobile.data.remote

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.HttpException
import ru.vsm.mobile.data.remote.dto.ErrorResponseDto
import ru.vsm.mobile.domain.error.DomainError

/**
 * Единая точка превращения Retrofit-исключений в [DomainError] внутри `Result`. Используется
 * всеми `*RepositoryImpl` вместо повторения одного и того же try/catch на каждый вызов.
 */
class SafeApiCall(private val json: Json) {

    suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Result.failure(toApiError(e))
    } catch (e: IOException) {
        Result.failure(DomainError.Network(e))
    } catch (e: SerializationException) {
        Result.failure(DomainError.Unexpected(e))
    } catch (e: Exception) {
        Result.failure(DomainError.Unexpected(e))
    }

    private fun toApiError(e: HttpException): DomainError.Api {
        val body: ResponseBody? = e.response()?.errorBody()
        val parsed = body?.string()?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { json.decodeFromString(ErrorResponseDto.serializer(), raw) }.getOrNull()
        }
        return DomainError.Api(
            statusCode = e.code(),
            errorCode = parsed?.error,
            errorMessage = parsed?.message ?: e.message(),
        )
    }
}
