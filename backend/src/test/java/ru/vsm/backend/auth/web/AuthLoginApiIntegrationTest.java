package ru.vsm.backend.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /api/auth/login} и {@code GET /api/auth/me}: успешный логин выдаёт токен, которым
 * {@code /me} возвращает тот же профиль; неверный пароль/логин и отсутствующий/битый токен -> 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthLoginApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private void register(String login, String email, String password) throws Exception {
        String body = """
                {"login":"%s","email":"%s","password":"%s","displayName":"Test"}
                """.formatted(login, email, password);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void loginThenMeReturnsSameProfile() throws Exception {
        register("login-user", "login-user@example.com", "password123");

        String loginBody = """
                {"login":"login-user","password":"password123"}
                """;
        String loginResponse = mockMvc.perform(
                        post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.profile.login").value("login-user"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("login-user"))
                .andExpect(jsonPath("$.email").value("login-user@example.com"));
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        register("wrong-pass-user", "wrong-pass@example.com", "password123");

        String loginBody = """
                {"login":"wrong-pass-user","password":"not-the-password"}
                """;
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void loginWithUnknownLoginReturnsUnauthorized() throws Exception {
        String loginBody = """
                {"login":"no-such-user","password":"password123"}
                """;
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void meWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meWithGarbageTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
