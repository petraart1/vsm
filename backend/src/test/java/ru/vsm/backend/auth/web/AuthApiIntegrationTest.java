package ru.vsm.backend.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import ru.vsm.backend.auth.repository.AppUserRepository;

/**
 * Регистрация: 201 без пароля в ответе, повтор логина -> 409, дефолтный админ создан при старте.
 * Публичные игровые эндпоинты в этом же контексте проверяются отдельно
 * ({@code ScenarioPlayApiIntegrationTest}) — этот тест подтверждает, что Security (permitAll на
 * этом шаге) им не мешает, не дублируя их сценарии здесь.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Value("${app.auth.admin.login}")
    private String adminLogin;

    @Test
    void registerReturnsCreatedProfileWithoutPassword() throws Exception {
        String body = """
                {"login":"conductor1","email":"conductor1@example.com","password":"password123","displayName":"Проводник"}
                """;

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("conductor1"))
                .andExpect(jsonPath("$.email").value("conductor1@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerWithTakenLoginReturnsConflict() throws Exception {
        String body = """
                {"login":"duplicate-login","email":"first@example.com","password":"password123","displayName":"A"}
                """;
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        String repeated = """
                {"login":"duplicate-login","email":"second@example.com","password":"password123","displayName":"B"}
                """;
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(repeated))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("login_already_taken"));
    }

    @Test
    void registerWithTakenEmailReturnsConflict() throws Exception {
        String body = """
                {"login":"login-a","email":"shared@example.com","password":"password123","displayName":"A"}
                """;
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        String repeated = """
                {"login":"login-b","email":"shared@example.com","password":"password123","displayName":"B"}
                """;
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(repeated))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email_already_taken"));
    }

    @Test
    void defaultAdminAccountIsCreatedOnStartup() {
        assertThat(appUserRepository.findByLogin(adminLogin)).isPresent();
        assertThat(appUserRepository.findByLogin(adminLogin).get().getRole().name()).isEqualTo("ADMIN");
    }
}
