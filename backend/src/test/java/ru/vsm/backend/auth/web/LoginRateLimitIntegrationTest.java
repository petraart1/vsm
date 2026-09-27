package ru.vsm.backend.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/**
 * {@code POST /api/auth/login} — лимит брутфорса по паре логин+IP (см.
 * {@code ru.vsm.backend.auth.security.LoginRateLimiter}, HIGH из аудита безопасности п. 3): после
 * {@code app.auth.login-rate-limit.max-attempts} неверных паролей подряд дальнейшие попытки этой
 * пары получают {@code 429 too_many_attempts}, даже с правильным паролем.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LoginRateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.auth.login-rate-limit.max-attempts}")
    private int maxAttempts;

    private void register(String login, String email, String password) throws Exception {
        String body = """
                {"login":"%s","email":"%s","password":"%s","displayName":"Test"}
                """.formatted(login, email, password);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void repeatedWrongPasswordLocksLoginThenBlocksEvenCorrectPassword() throws Exception {
        register("rate-limit-user", "rate-limit@example.com", "password123");
        String wrongBody = """
                {"login":"rate-limit-user","password":"not-the-password"}
                """;
        String correctBody = """
                {"login":"rate-limit-user","password":"password123"}
                """;

        for (int i = 0; i < maxAttempts; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongBody))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("too_many_attempts"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(correctBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("too_many_attempts"));
    }
}
