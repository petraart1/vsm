package ru.vsm.backend.auth.web.esia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** {@code app.esia.mock.enabled=false} -> контроллер не регистрируется, все три пути отвечают 404. */
@SpringBootTest(properties = "app.esia.mock.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class EsiaMockDisabledIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authorizeReturnsNotFoundWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/auth/esia/authorize").param("redirect_uri", "http://localhost:3000/esia"))
                .andExpect(status().isNotFound());
    }

    @Test
    void selectReturnsNotFoundWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/auth/esia/select")
                        .param("redirect_uri", "http://localhost:3000/esia")
                        .param("citizen", "ivanova"))
                .andExpect(status().isNotFound());
    }

    @Test
    void callbackReturnsNotFoundWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/esia/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"anything\"}"))
                .andExpect(status().isNotFound());
    }
}
