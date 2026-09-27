package ru.vsm.mobile.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import ru.vsm.mobile.data.remote.dto.EsiaCallbackRequestDto
import ru.vsm.mobile.data.remote.dto.LoginRequestDto
import ru.vsm.mobile.data.remote.dto.LoginResponseDto
import ru.vsm.mobile.data.remote.dto.RegisterRequestDto
import ru.vsm.mobile.data.remote.dto.UserProfileResponseDto

/** `ru.vsm.backend.auth.web.AuthController` + `ru.vsm.backend.auth.web.esia.EsiaMockController` */
interface AuthApi {

    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequestDto,
        /** Playerid уже накопленной анонимной сессии — тот же заголовок, что и у игровых эндпоинтов ([ScenarioApi.PLAYER_ID_HEADER]). */
        @Header(ScenarioApi.PLAYER_ID_HEADER) anonymousPlayerId: String?,
    ): UserProfileResponseDto

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto

    /** `Authorization: Bearer <token>` добавляется [ru.vsm.mobile.data.remote.AuthInterceptor] автоматически. */
    @GET("api/auth/me")
    suspend fun me(): UserProfileResponseDto

    /**
     * Обмен кода авторизации демо-ЕСИА на токен — тот же формат ответа, что [login]. `GET
     * .../authorize` и `.../select` (HTML-страница выбора гражданина и её редирект с кодом)
     * не JSON-эндпоинты — открываются в веб-вьюхе напрямую по URL, без Retrofit.
     */
    @POST("api/auth/esia/callback")
    suspend fun esiaCallback(@Body request: EsiaCallbackRequestDto): LoginResponseDto
}
