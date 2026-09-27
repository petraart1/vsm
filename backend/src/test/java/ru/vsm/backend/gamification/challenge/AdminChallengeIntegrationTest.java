package ru.vsm.backend.gamification.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** Админ создаёт событие и видит его в списке; без роли ADMIN — 401/403. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminChallengeIntegrationTest {

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
                        .content("""
                                {"login":"%s","password":"%s"}
                                """.formatted(adminLogin, adminPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String body() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(7, ChronoUnit.DAYS);
        return """
                {"title":"Неделя безопасности","description":"Пройдите 3 ситуации блока без критических ошибок",
                 "goalType":"BLOCK_SCENARIOS_NO_FAILURE","targetBlock":"safety","targetCount":3,
                 "startsAt":"%s","endsAt":"%s","rewardPoints":300,"rewardAchievementCode":"CHALLENGE_CHAMPION"}
                """.formatted(start, end);
    }

    @Test
    void adminCreatesEventAndItBecomesActive() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/admin/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.targetBlock").value("safety"));

        mockMvc.perform(get("/api/gamification/challenges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Неделя безопасности')]").exists());
    }

    @Test
    void anonymousCannotCreateEvent() throws Exception {
        mockMvc.perform(post("/api/admin/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().is4xxClientError());
    }
}
