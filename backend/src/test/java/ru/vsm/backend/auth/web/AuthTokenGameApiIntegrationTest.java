package ru.vsm.backend.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.scenario.repository.ScenarioRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Взаимодействие фильтра JWT (шаг 2 авторизации) с игровыми REST-эндпоинтами домена scenario,
 * которые сами не менялись: playerId из валидного {@code Authorization: Bearer} побеждает
 * {@code X-Player-Id}, если оба присутствуют и различаются; без токена всё работает как раньше;
 * регистрация с существующим анонимным {@code X-Player-Id} делает его id учётной записи, поэтому
 * прогресс, накопленный анонимно, остаётся доступен под тем же id после регистрации.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthTokenGameApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void tokenPlayerIdWinsOverDifferentXPlayerIdHeader() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();

        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"token-owner","email":"token-owner@example.com","password":"password123","displayName":"T"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID tokenOwnerId = UUID.fromString(objectMapper.readTree(registerResponse).get("id").asText());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"token-owner","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).get("token").asText();

        UUID unrelatedPlayerId = UUID.randomUUID();

        // Оба заголовка присутствуют и различаются -> владельцем становится playerId из токена,
        // не unrelatedPlayerId из X-Player-Id.
        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Player-Id", unrelatedPlayerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(startResponse).get("progressId").asText());

        // Владелец — tokenOwnerId: доступ по его X-Player-Id (без токена) проходит.
        mockMvc.perform(get("/api/scenarios/progress/{progressId}", progressId)
                        .header("X-Player-Id", tokenOwnerId.toString()))
                .andExpect(status().isOk());

        // unrelatedPlayerId владельцем не стал, несмотря на то что был в заголовке запроса старта.
        mockMvc.perform(get("/api/scenarios/progress/{progressId}", progressId)
                        .header("X-Player-Id", unrelatedPlayerId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void gameEndpointWithoutTokenStillUsesXPlayerIdAsBefore() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        UUID anonymousPlayerId = UUID.randomUUID();

        mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("X-Player-Id", anonymousPlayerId.toString()))
                .andExpect(status().isCreated());
    }

    @Test
    void registrationWithAnonymousPlayerIdKeepsSameIdForExistingProgress() throws Exception {
        UUID scenarioId = scenarioRepository.findByCode("boarding-no-ticket").orElseThrow().getId();
        UUID anonymousPlayerId = UUID.randomUUID();

        String startResponse = mockMvc.perform(post("/api/scenarios/{id}/progress", scenarioId)
                        .header("X-Player-Id", anonymousPlayerId.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID progressId = UUID.fromString(objectMapper.readTree(startResponse).get("progressId").asText());

        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .header("X-Player-Id", anonymousPlayerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"anon-turned-user","email":"anon-turned-user@example.com","password":"password123","displayName":"A"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(anonymousPlayerId.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID accountId = UUID.fromString(objectMapper.readTree(registerResponse).get("id").asText());
        assertThat(accountId).isEqualTo(anonymousPlayerId);

        // Прогресс, начатый анонимно, всё ещё доступен под тем же id — теперь это id учётной записи.
        mockMvc.perform(get("/api/scenarios/progress/{progressId}", progressId)
                        .header("X-Player-Id", anonymousPlayerId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void registrationWithAlreadyRegisteredPlayerIdReturnsConflict() throws Exception {
        String firstRegisterResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"already-has-account","email":"already-has-account@example.com","password":"password123","displayName":"A"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID existingAccountId = UUID.fromString(objectMapper.readTree(firstRegisterResponse).get("id").asText());

        mockMvc.perform(post("/api/auth/register")
                        .header("X-Player-Id", existingAccountId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"second-login","email":"second-login@example.com","password":"password123","displayName":"B"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("player_already_registered"));
    }
}
