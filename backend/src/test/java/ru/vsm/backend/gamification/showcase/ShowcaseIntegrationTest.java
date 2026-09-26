package ru.vsm.backend.gamification.showcase;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import ru.vsm.backend.auth.security.PlayerPublicIdService;

/** Витрина наград: игрок заменяет свою витрину, коллеги читают её по publicId. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ShowcaseIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerPublicIdService publicIdService;

    private static final String BODY = """
            {"finish":"glass","items":[
              {"id":"shift:clean","title":"Смена без замечаний","shape":"circle","glyph":"train"},
              {"id":"streak:7","title":"Серия 7 дней","shape":"circle","text":"7"}
            ]}
            """;

    @Test
    void ownerReplacesShowcaseAndColleagueReadsItByPublicId() throws Exception {
        UUID playerId = UUID.randomUUID();
        mockMvc.perform(put("/api/gamification/showcase")
                        .header("X-Player-Id", playerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/gamification/showcase/" + publicIdService.publicId(playerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finish").value("glass"))
                .andExpect(jsonPath("$.items[0].id").value("shift:clean"))
                .andExpect(jsonPath("$.items[1].text").value("7"));
    }

    @Test
    void moreThanSixItemsIsRejected() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"id\":\"dist:A").append(i).append("\",\"title\":\"x\",\"shape\":\"hexagon\"}");
        }
        mockMvc.perform(put("/api/gamification/showcase")
                        .header("X-Player-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + items + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCannotReplaceShowcase() throws Exception {
        mockMvc.perform(put("/api/gamification/showcase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().is4xxClientError());
    }
}
