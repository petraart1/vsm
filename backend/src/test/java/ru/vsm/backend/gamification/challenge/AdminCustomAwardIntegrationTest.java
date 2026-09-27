package ru.vsm.backend.gamification.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/** Админ создаёт награду и выдаёт её игроку; игрок видит её полученной в каталоге. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminCustomAwardIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.auth.admin.login}")
    private String adminLogin;

    @Value("${app.auth.admin.password}")
    private String adminPassword;

    private String adminToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(adminLogin, adminPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void adminCreatesAndGrantsAward() throws Exception {
        String token = adminToken();
        String created = mockMvc.perform(post("/api/admin/awards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Наставник","description":"Помог новичку на смене","shape":"shield","glyph":"users","verifiedOnly":false}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String awardId = objectMapper.readTree(created).get("id").asText();

        UUID playerId = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/awards/" + awardId + "/grant")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player\":\"" + playerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedCount").value(1));

        mockMvc.perform(get("/api/gamification/custom-awards").param("playerId", playerId.toString())
                        .header("X-Player-Id", playerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Наставник')].earned").value(true));
    }

    @Test
    void verifiedOnlyAwardIsNotGrantedToUnverifiedPlayer() throws Exception {
        String token = adminToken();
        String created = mockMvc.perform(post("/api/admin/awards")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Официальная","description":"Только для подтверждённых","shape":"circle","verifiedOnly":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String awardId = objectMapper.readTree(created).get("id").asText();
        mockMvc.perform(post("/api/admin/awards/" + awardId + "/grant")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"player\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }
}
