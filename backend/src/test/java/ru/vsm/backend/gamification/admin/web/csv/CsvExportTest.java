package ru.vsm.backend.gamification.admin.web.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Юнит-тест на саму сборку CSV (без Spring/БД) — заголовки ответа, UTF-8 BOM в начале тела,
 * разделитель {@code ;} и экранирование кавычек/разделителя внутри значения (см. {@link CsvExport}
 * javadoc про выбор формата под Excel с русской локалью).
 */
class CsvExportTest {

    @Test
    void responseHasCsvContentTypeAndAttachmentDisposition() {
        ResponseEntity<byte[]> response = CsvExport.toCsvResponse(
                "players.csv", List.of("a", "b"), List.of(List.of("1", "2")));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"players.csv\"");
    }

    @Test
    void bodyStartsWithUtf8BomAndUsesSemicolonDelimiter() {
        ResponseEntity<byte[]> response = CsvExport.toCsvResponse(
                "scenarios.csv", List.of("code", "title"), List.of(List.of("boarding-no-ticket", "Нет билета")));
        byte[] body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);

        String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        assertThat(text).isEqualTo("code;title\r\nboarding-no-ticket;Нет билета\r\n");
    }

    @Test
    void escapesDelimiterAndQuoteInsideValue() {
        ResponseEntity<byte[]> response = CsvExport.toCsvResponse(
                "teams.csv",
                List.of("name"),
                List.of(List.of("Бригада \"Север\"; депо Москва")));
        String text = new String(response.getBody(), 3, response.getBody().length - 3, StandardCharsets.UTF_8);

        // исходное значение содержит ';' и '"' -> всё поле в кавычках, внутренние '"' удвоены.
        assertThat(text).isEqualTo("name\r\n\"Бригада \"\"Север\"\"; депо Москва\"\r\n");
    }

    @Test
    void plainValueWithoutSpecialCharactersIsNotQuoted() {
        ResponseEntity<byte[]> response = CsvExport.toCsvResponse(
                "blocks.csv", List.of("block"), List.of(List.of("boarding")));
        String text = new String(response.getBody(), 3, response.getBody().length - 3, StandardCharsets.UTF_8);

        assertThat(text).isEqualTo("block\r\nboarding\r\n");
    }
}
