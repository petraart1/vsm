package ru.vsm.mobile.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import ru.vsm.mobile.data.remote.dto.LoginRequestDto
import ru.vsm.mobile.data.remote.dto.LoginResponseDto
import ru.vsm.mobile.data.remote.dto.RegisterRequestDto
import ru.vsm.mobile.data.remote.dto.UserProfileResponseDto

/** `ru.vsm.backend.auth.web.AuthController` */
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
}
