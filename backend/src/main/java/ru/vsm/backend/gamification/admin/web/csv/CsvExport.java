package ru.vsm.backend.gamification.admin.web.csv;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Сборка CSV-ответов для экспорта статистики администратора ({@code AdminStatsController}).
 *
 * <p>Формат намеренно рассчитан на открытие в Excel с русской региональной настройкой, а не на
 * "нейтральный" RFC 4180: поля разделяются точкой с запятой {@code ;}, а не запятой — в русской
 * локали запятая уже занята как десятичный разделитель, поэтому список Excel ждёт от CSV именно
 * {@code ;} как разделитель полей и без этого разложит одну строку на лишние столбцы вместо ячеек.
 * По той же причине файл начинается с UTF-8 BOM ({@code EF BB BF}): без него Excel определяет
 * кодировку эвристически и на кириллице почти всегда ошибается, показывая "кракозябры" вместо
 * текста. Числа с плавающей точкой при этом форматируются с {@code .} как разделителем дробной
 * части (см. {@code AdminStatsController#formatRate}/{@code #formatScore}) — точка не участвует в
 * разборе полей, конфликта с {@code ;} нет, а значения остаются однозначно машиночитаемыми.
 */
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

    /**
     * Оборачивает значение в кавычки и удваивает внутренние кавычки, если оно содержит разделитель,
     * кавычку или перевод строки — стандартное экранирование CSV (RFC 4180), делимитер здесь не
     * влияет на выбор способа экранирования, только на то, что именно его наличие требует кавычек.
     */
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
