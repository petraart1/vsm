package ru.vsm.backend.gamification.admin.web;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vsm.backend.gamification.admin.service.AdminStatsService;
import ru.vsm.backend.gamification.admin.web.csv.CsvExport;
import ru.vsm.backend.gamification.admin.web.dto.BlockStatsEntryDto;
import ru.vsm.backend.gamification.admin.web.dto.OverviewStatsResponse;
import ru.vsm.backend.gamification.admin.web.dto.PlayerStatsEntryDto;
import ru.vsm.backend.gamification.admin.web.dto.ScenarioStatsEntryDto;
import ru.vsm.backend.gamification.team.web.dto.TeamLeaderboardEntryDto;

/**
 * Read-only статистика для административной панели. Доступ ограничен ролью {@code ADMIN} через
 * {@code /api/admin/**} (см. {@code SecurityConfig}) — не выдаёт персональных данных сверх уже
 * открытых лидерборда/профиля, но требует токен, в отличие от них.
 *
 * <p>Каждый JSON-эндпоинт продублирован CSV-версией ({@code *.csv}) для выгрузки в Excel — см.
 * {@link CsvExport} про выбор {@code ;} как разделителя и UTF-8 BOM.
 */
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService statsService;

    @GetMapping("/overview")
    public OverviewStatsResponse getOverview() {
        return statsService.getOverview();
    }

    @GetMapping("/scenarios")
    public List<ScenarioStatsEntryDto> getScenarioStats() {
        return statsService.getScenarioStats();
    }

    @GetMapping("/blocks")
    public List<BlockStatsEntryDto> getBlockStats() {
        return statsService.getBlockStats();
    }

    @GetMapping("/scenarios.csv")
    public ResponseEntity<byte[]> getScenarioStatsCsv() {
        List<String> header = List.of("code", "title", "block", "totalPlaythroughs", "completedPlaythroughs",
                "successRate", "avgLoyaltyScore", "avgSafetyScore", "timeoutRate");
        List<List<String>> rows = statsService.getScenarioStats().stream()
                .map(r -> List.of(
                        orEmpty(r.code()),
                        orEmpty(r.title()),
                        orEmpty(r.block()),
                        String.valueOf(r.totalPlaythroughs()),
                        String.valueOf(r.completedPlaythroughs()),
                        formatRate(r.successRate()),
                        formatScore(r.avgLoyaltyScore()),
                        formatScore(r.avgSafetyScore()),
                        formatRate(r.timeoutRate())))
                .toList();
        return CsvExport.toCsvResponse("scenarios.csv", header, rows);
    }

    @GetMapping("/blocks.csv")
    public ResponseEntity<byte[]> getBlockStatsCsv() {
        List<String> header = List.of("block", "totalPlaythroughs", "completedPlaythroughs", "successRate",
                "avgLoyaltyScore", "avgSafetyScore");
        List<List<String>> rows = statsService.getBlockStats().stream()
                .map(r -> List.of(
                        orEmpty(r.block()),
                        String.valueOf(r.totalPlaythroughs()),
                        String.valueOf(r.completedPlaythroughs()),
                        formatRate(r.successRate()),
                        formatScore(r.avgLoyaltyScore()),
                        formatScore(r.avgSafetyScore())))
                .toList();
        return CsvExport.toCsvResponse("blocks.csv", header, rows);
    }

    @GetMapping("/players.csv")
    public ResponseEntity<byte[]> getPlayerStatsCsv() {
        List<String> header = List.of("playerId", "displayName", "team", "totalScore", "totalPlaythroughs",
                "successRate", "avgLoyaltyScore", "avgSafetyScore", "lastActivity");
        List<List<String>> rows = statsService.getPlayerStats().stream()
                .map(r -> List.of(
                        r.playerId().toString(),
                        orEmpty(r.displayName()),
                        orEmpty(r.teamName()),
                        String.valueOf(r.totalScore()),
                        String.valueOf(r.totalPlaythroughs()),
                        formatRate(r.successRate()),
                        formatScore(r.avgLoyaltyScore()),
                        formatScore(r.avgSafetyScore()),
                        r.lastActivity() == null ? "" : r.lastActivity().toString()))
                .toList();
        return CsvExport.toCsvResponse("players.csv", header, rows);
    }

    @GetMapping("/teams.csv")
    public ResponseEntity<byte[]> getTeamStatsCsv() {
        List<String> header = List.of("rank", "code", "name", "depot", "memberCount", "totalScore", "averageScore",
                "totalPlaythroughs", "averageSafety");
        List<List<String>> rows = statsService.getTeamStats().stream()
                .map(this::toTeamCsvRow)
                .toList();
        return CsvExport.toCsvResponse("teams.csv", header, rows);
    }

    private List<String> toTeamCsvRow(TeamLeaderboardEntryDto r) {
        return List.of(
                String.valueOf(r.rank()),
                orEmpty(r.code()),
                orEmpty(r.name()),
                orEmpty(r.depot()),
                String.valueOf(r.memberCount()),
                String.valueOf(r.totalScore()),
                formatScore(r.averageScore()),
                String.valueOf(r.totalPlaythroughs()),
                formatScore(r.averageSafety()));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Доля (0..1) с 4 знаками после точки — точка не разделитель полей, конфликта с {@code ;} нет. */
    private static String formatRate(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String formatScore(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
