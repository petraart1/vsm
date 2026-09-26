package ru.vsm.backend.auth.web.esia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Полный демо-поток ЕСИА: {@code authorize} отдаёт страницу выбора, {@code select} редиректит с
 * {@code code}, {@code callback} обменивает код на JWT с {@code verified=true}; повторный вход тем
 * же тестовым гражданином не заводит вторую учётную запись.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EsiaMockLoginIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authorizePageListsTestCitizens() throws Exception {
        mockMvc.perform(get("/api/auth/esia/authorize").param("redirect_uri", "http://localhost:3000/esia"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith(MediaType.TEXT_HTML_VALUE)))
                .andExpect(content("ivanova"))
                .andExpect(content("Госуслуги (демо)"));
    }

    @Test
    void fullFlowIssuesVerifiedJwt() throws Exception {
        String redirectUri = "http://localhost:3000/esia-callback";

        String location = mockMvc.perform(get("/api/auth/esia/select")
                        .param("redirect_uri", redirectUri)
                        .param("citizen", "petrov"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith(redirectUri + "?code=");
        String code = location.substring((redirectUri + "?code=").length());

        String callbackBody = """
                {"code":"%s"}
                """.formatted(code);
        String response = mockMvc.perform(
                        post("/api/auth/esia/callback").contentType(MediaType.APPLICATION_JSON).content(callbackBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.profile.login").value("esia_petrov"))
                .andExpect(jsonPath("$.profile.displayName").value("Петров Алексей Викторович"))
                .andExpect(jsonPath("$.profile.verified").value(true))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();
        String userId = objectMapper.readTree(response).get("profile").get("id").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.id").value(userId));
    }

    @Test
    void secondLoginBySameCitizenReusesSameAccount() throws Exception {
        String redirectUri = "http://localhost:3000/esia-callback";

        String firstUserId = loginAsAndReturnUserId(redirectUri, "sidorova");
        String secondUserId = loginAsAndReturnUserId(redirectUri, "sidorova");

        assertThat(secondUserId).isEqualTo(firstUserId);
    }

    @Test
    void unknownCodeOnCallbackReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/esia/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"not-a-real-code\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_or_expired_esia_code"));
    }

    @Test
    void unknownCitizenOnSelectReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/auth/esia/select")
                        .param("redirect_uri", "http://localhost:3000/esia")
                        .param("citizen", "no-such-citizen"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown_test_citizen"));
    }

    private String loginAsAndReturnUserId(String redirectUri, String citizenCode) throws Exception {
        String location = mockMvc.perform(get("/api/auth/esia/select")
                        .param("redirect_uri", redirectUri)
                        .param("citizen", citizenCode))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        String code = location.substring((redirectUri + "?code=").length());

        String response = mockMvc.perform(post("/api/auth/esia/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(code)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("profile").get("id").asText();
    }

    private static org.springframework.test.web.servlet.ResultMatcher content(String expectedSubstring) {
        return result -> assertThat(result.getResponse().getContentAsString()).contains(expectedSubstring);
    }
}
