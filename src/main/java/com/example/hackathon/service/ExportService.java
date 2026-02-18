package com.example.hackathon.service;

import com.example.hackathon.dto.LeaderboardEntryResponse;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.model.Evaluation;
import com.example.hackathon.model.HackathonEvent;
import com.example.hackathon.model.Payment;
import com.example.hackathon.model.Team;
import com.example.hackathon.model.TeamMember;
import com.example.hackathon.repository.EvaluationRepository;
import com.example.hackathon.repository.PaymentRepository;
import com.example.hackathon.repository.TeamMemberRepository;
import com.example.hackathon.repository.TeamRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private static final DateTimeFormatter INSTANT_FORMAT = DateTimeFormatter.ISO_INSTANT;

    private final EventService eventService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PaymentRepository paymentRepository;
    private final EvaluationRepository evaluationRepository;
    private final LeaderboardService leaderboardService;

    public ExportService(EventService eventService,
                         TeamRepository teamRepository,
                         TeamMemberRepository teamMemberRepository,
                         PaymentRepository paymentRepository,
                         EvaluationRepository evaluationRepository,
                         LeaderboardService leaderboardService) {
        this.eventService = eventService;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.paymentRepository = paymentRepository;
        this.evaluationRepository = evaluationRepository;
        this.leaderboardService = leaderboardService;
    }

    public ExportFile exportEventData(String eventId, String datasetRaw, String formatRaw) {
        HackathonEvent event = eventService.getEventEntity(eventId);
        ExportDataset dataset = ExportDataset.from(datasetRaw);
        ExportFormat format = ExportFormat.from(formatRaw);

        ExportTable table = switch (dataset) {
            case TEAMS -> buildTeamsTable(eventId);
            case PAYMENTS -> buildPaymentsTable(eventId);
            case LEADERBOARD -> buildLeaderboardTable(eventId);
            case EVALUATIONS -> buildEvaluationsTable(eventId);
        };

        byte[] content = switch (format) {
            case CSV -> buildCsv(table);
            case XLSX -> buildExcel(table);
        };

        String filename = buildFilename(event.getTitle(), dataset.fileSuffix, format.fileExtension);
        return new ExportFile(filename, format.contentType, content);
    }

    private ExportTable buildTeamsTable(String eventId) {
        List<Team> teams = teamRepository.findByEventId(eventId).stream()
                .sorted(Comparator.comparing(team -> safeText(team.getTeamName()), String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, List<TeamMember>> membersByTeam = loadMembersByTeam(teams);

        List<String> headers = List.of(
                "Team ID",
                "Team Name",
                "Team Size",
                "Payment Status",
                "Total Score",
                "Problem Statement ID",
                "Problem Statement Title",
                "Leader Name",
                "Leader Email",
                "Leader Phone",
                "Members",
                "Form Responses",
                "Created At"
        );

        List<List<String>> rows = new ArrayList<>();
        for (Team team : teams) {
            List<TeamMember> members = membersByTeam.getOrDefault(team.getId(), List.of());
            TeamMember leader = members.stream().filter(TeamMember::isLeader).findFirst().orElse(null);

            rows.add(List.of(
                    safeText(team.getId()),
                    safeText(team.getTeamName()),
                    String.valueOf(team.getTeamSize()),
                    team.getPaymentStatus() == null ? "" : team.getPaymentStatus().name(),
                    formatDecimal(team.getTotalScore()),
                    safeText(team.getSelectedProblemStatementId()),
                    safeText(team.getSelectedProblemStatementTitle()),
                    leader == null ? "" : safeText(leader.getName()),
                    leader == null ? "" : safeText(leader.getEmail()),
                    leader == null ? "" : safeText(leader.getPhone()),
                    renderMembers(members),
                    renderFormResponses(team.getFormResponses()),
                    formatInstant(team.getCreatedAt())
            ));
        }

        return new ExportTable("Teams", headers, rows);
    }

    private ExportTable buildPaymentsTable(String eventId) {
        List<Payment> payments = paymentRepository.findByEventId(eventId).stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, Team> teamsById = loadTeamsById(payments.stream()
                .map(Payment::getTeamId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<String> headers = List.of(
                "Payment ID",
                "Team ID",
                "Team Name",
                "Amount",
                "Currency",
                "Payment Record Status",
                "Team Payment Status",
                "Razorpay Order ID",
                "Razorpay Payment ID",
                "Created At",
                "Verified At"
        );

        List<List<String>> rows = new ArrayList<>();
        for (Payment payment : payments) {
            Team team = teamsById.get(payment.getTeamId());
            rows.add(List.of(
                    safeText(payment.getId()),
                    safeText(payment.getTeamId()),
                    team == null ? "" : safeText(team.getTeamName()),
                    formatBigDecimal(payment.getAmount()),
                    safeText(payment.getCurrency()),
                    payment.getStatus() == null ? "" : payment.getStatus().name(),
                    team == null || team.getPaymentStatus() == null ? "" : team.getPaymentStatus().name(),
                    safeText(payment.getRazorpayOrderId()),
                    safeText(payment.getRazorpayPaymentId()),
                    formatInstant(payment.getCreatedAt()),
                    formatInstant(payment.getVerifiedAt())
            ));
        }

        return new ExportTable("Payments", headers, rows);
    }

    private ExportTable buildLeaderboardTable(String eventId) {
        List<LeaderboardEntryResponse> leaderboard = leaderboardService.getLeaderboard(eventId, true);
        Map<String, Team> teamsById = loadTeamsById(leaderboard.stream()
                .map(LeaderboardEntryResponse::teamId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<String> headers = List.of(
                "Rank",
                "Team ID",
                "Team Name",
                "Total Score",
                "Payment Status",
                "Problem Statement Title"
        );

        List<List<String>> rows = new ArrayList<>();
        for (LeaderboardEntryResponse entry : leaderboard) {
            Team team = teamsById.get(entry.teamId());
            rows.add(List.of(
                    String.valueOf(entry.rank()),
                    safeText(entry.teamId()),
                    safeText(entry.teamName()),
                    formatDecimal(entry.totalScore()),
                    team == null || team.getPaymentStatus() == null ? "" : team.getPaymentStatus().name(),
                    team == null ? "" : safeText(team.getSelectedProblemStatementTitle())
            ));
        }

        return new ExportTable("Leaderboard", headers, rows);
    }

    private ExportTable buildEvaluationsTable(String eventId) {
        List<Evaluation> evaluations = evaluationRepository.findByEventId(eventId).stream()
                .sorted(Comparator.comparing(Evaluation::getEvaluatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<String, Team> teamsById = loadTeamsById(evaluations.stream()
                .map(Evaluation::getTeamId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<String> headers = List.of(
                "Evaluation ID",
                "Team ID",
                "Team Name",
                "Criterion ID",
                "Criterion Name",
                "Marks Given",
                "Max Marks",
                "Total Score",
                "Evaluated By",
                "Description",
                "Evaluated At"
        );

        List<List<String>> rows = new ArrayList<>();
        for (Evaluation evaluation : evaluations) {
            Team team = teamsById.get(evaluation.getTeamId());
            rows.add(List.of(
                    safeText(evaluation.getId()),
                    safeText(evaluation.getTeamId()),
                    team == null ? "" : safeText(team.getTeamName()),
                    safeText(evaluation.getCriterionId()),
                    safeText(evaluation.getCriterionName()),
                    formatDecimal(evaluation.getMarksGiven()),
                    formatDecimal(evaluation.getMaxMarks()),
                    formatDecimal(evaluation.getTotalScore()),
                    safeText(evaluation.getEvaluatedBy()),
                    safeText(evaluation.getDescription()),
                    formatInstant(evaluation.getEvaluatedAt())
            ));
        }

        return new ExportTable("Evaluations", headers, rows);
    }

    private Map<String, List<TeamMember>> loadMembersByTeam(List<Team> teams) {
        if (teams.isEmpty()) {
            return Map.of();
        }

        List<String> teamIds = teams.stream()
                .map(Team::getId)
                .filter(Objects::nonNull)
                .toList();

        return teamMemberRepository.findByTeamIdIn(teamIds).stream()
                .collect(Collectors.groupingBy(TeamMember::getTeamId));
    }

    private Map<String, Team> loadTeamsById(Collection<String> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Map.of();
        }

        List<String> sanitizedIds = teamIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        if (sanitizedIds.isEmpty()) {
            return Map.of();
        }

        return teamRepository.findAllById(sanitizedIds).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity(), (left, right) -> left));
    }

    private String renderMembers(List<TeamMember> members) {
        if (members == null || members.isEmpty()) {
            return "";
        }

        return members.stream()
                .sorted(Comparator.comparing(TeamMember::isLeader).reversed()
                        .thenComparing(member -> safeText(member.getName()), String.CASE_INSENSITIVE_ORDER))
                .map(member -> (member.isLeader() ? "[Leader] " : "")
                        + safeText(member.getName())
                        + " <" + safeText(member.getEmail()) + ">"
                        + (member.getPhone() == null || member.getPhone().isBlank() ? "" : " " + member.getPhone()))
                .collect(Collectors.joining(" | "));
    }

    private String renderFormResponses(Map<String, Object> formResponses) {
        if (formResponses == null || formResponses.isEmpty()) {
            return "";
        }

        return formResponses.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + stringifyObject(entry.getValue()))
                .collect(Collectors.joining(" | "));
    }

    private String stringifyObject(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::stringifyObject).collect(Collectors.joining(", "));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
                    .map(entry -> String.valueOf(entry.getKey()) + ":" + stringifyObject(entry.getValue()))
                    .collect(Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private byte[] buildCsv(ExportTable table) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, table.headers());
        for (List<String> row : table.rows()) {
            appendCsvRow(csv, row);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendCsvRow(StringBuilder builder, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escapeCsv(row.get(i)));
        }
        builder.append('\n');
    }

    private String escapeCsv(String rawValue) {
        String value = rawValue == null ? "" : rawValue;
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private byte[] buildExcel(ExportTable table) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(table.sheetName());

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < table.headers().size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(table.headers().get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (List<String> rowData : table.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < rowData.size(); i++) {
                    row.createCell(i).setCellValue(rowData.get(i));
                }
            }

            for (int i = 0; i < table.headers().size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BadRequestException("Failed to build export file");
        }
    }

    private String buildFilename(String eventTitle, String datasetSuffix, String extension) {
        String slug = sanitizeForFilename(eventTitle);
        String datePart = LocalDate.now().toString();
        return slug + "-" + datasetSuffix + "-" + datePart + "." + extension;
    }

    private String sanitizeForFilename(String input) {
        String normalized = safeText(input)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "event" : normalized;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatInstant(Instant value) {
        return value == null ? "" : INSTANT_FORMAT.format(value);
    }

    private String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatBigDecimal(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    public record ExportFile(String fileName, String contentType, byte[] content) {
    }

    private record ExportTable(String sheetName, List<String> headers, List<List<String>> rows) {
    }

    private enum ExportDataset {
        TEAMS("teams"),
        PAYMENTS("payments"),
        LEADERBOARD("leaderboard"),
        EVALUATIONS("evaluations");

        private final String fileSuffix;

        ExportDataset(String fileSuffix) {
            this.fileSuffix = fileSuffix;
        }

        private static ExportDataset from(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new BadRequestException("Dataset is required. Allowed values: teams, payments, leaderboard, evaluations");
            }

            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "teams" -> TEAMS;
                case "payments" -> PAYMENTS;
                case "leaderboard" -> LEADERBOARD;
                case "evaluations" -> EVALUATIONS;
                default -> throw new BadRequestException("Invalid dataset. Allowed values: teams, payments, leaderboard, evaluations");
            };
        }
    }

    private enum ExportFormat {
        CSV("csv", "text/csv"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final String fileExtension;
        private final String contentType;

        ExportFormat(String fileExtension, String contentType) {
            this.fileExtension = fileExtension;
            this.contentType = contentType;
        }

        private static ExportFormat from(String raw) {
            if (raw == null || raw.isBlank()) {
                return CSV;
            }

            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "csv" -> CSV;
                case "xlsx", "excel" -> XLSX;
                default -> throw new BadRequestException("Invalid format. Allowed values: csv, xlsx");
            };
        }
    }
}
