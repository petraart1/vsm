package ru.vsm.backend.gamification.admin.web.csv;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Сборка CSV-ответов для экспорта статистики администратора ({@code AdminStatsController}). */
public final class CsvExport {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final char DELIMITER = ';';
    private static final String LINE_BREAK = "\r\n";

    private CsvExport() {
    }

    /** Готовый {@link ResponseEntity} с заголовками {@code Content-Type}/{@code Content-Disposition}. */
    public static ResponseEntity<byte[]> toCsvResponse(String filename, List<String> header, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, header);
        for (List<String> row : rows) {
            appendRow(csv, row);
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(UTF8_BOM);
        body.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body.toByteArray());
    }

    private static void appendRow(StringBuilder csv, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                csv.append(DELIMITER);
            }
            csv.append(escape(fields.get(i)));
        }
        csv.append(LINE_BREAK);
    }

    /** Оборачивает значение в кавычки и удваивает внутренние кавычки, если оно содержит разделитель, */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuoting = value.indexOf(DELIMITER) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
