package top.yzljc.atribot.function.command.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.function.command.weather.WeatherReport.Phenomenon;
import top.yzljc.atribot.function.command.weather.WeatherReport.WeatherType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
public final class WeatherDataService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration CURRENT_WINDOW = Duration.ofHours(24);
    private static final int BASELINE_DAYS = 7;
    private static final int MIN_BASELINE_COVERAGE_MINUTES = 12 * 60;
    private static final Pattern GAME_COMMAND = Pattern.compile(
            "(?iu)(?:^|\\s)/(?:games?|小游戏|游戏|minesweeper|扫雷|sl|connect4|connectfour|四子棋|c4|roulette|幸运轮盘|rr|rsp|石头剪刀布)(?:\\s|$)"
    );

    private WeatherDataService() {
    }

    public static WeatherReport load(String groupOpenId, String excludedMessageId) {
        Instant now = Instant.now();
        Instant fetchFrom = now.minus(CURRENT_WINDOW.multipliedBy(BASELINE_DAYS + 1L));
        List<MessageSample> messages = new ArrayList<>();
        Instant firstRecordedAt = null;

        String minSql = "SELECT MIN(created_at) FROM `official_group_record` " +
                "WHERE group_openId = ? AND sender_is_bot = FALSE";
        String rowsSql = "SELECT union_openId, content, attachments, created_at FROM `official_group_record` " +
                "WHERE group_openId = ? AND sender_is_bot = FALSE AND created_at >= ? " +
                "AND (? IS NULL OR message_openId IS NULL OR message_openId <> ?) ORDER BY created_at ASC, id ASC";

        try (var conn = DatabaseManager.getConnection()) {
            try (var minStmt = conn.prepareStatement(minSql)) {
                minStmt.setString(1, groupOpenId);
                try (var rs = minStmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp value = rs.getTimestamp(1);
                        if (value != null) firstRecordedAt = value.toInstant();
                    }
                }
            }

            try (var rowsStmt = conn.prepareStatement(rowsSql)) {
                rowsStmt.setString(1, groupOpenId);
                rowsStmt.setTimestamp(2, Timestamp.from(fetchFrom));
                if (excludedMessageId == null || excludedMessageId.isBlank()) {
                    rowsStmt.setNull(3, java.sql.Types.VARCHAR);
                    rowsStmt.setNull(4, java.sql.Types.VARCHAR);
                } else {
                    rowsStmt.setString(3, excludedMessageId);
                    rowsStmt.setString(4, excludedMessageId);
                }
                try (var rs = rowsStmt.executeQuery()) {
                    while (rs.next()) {
                        Timestamp createdAt = rs.getTimestamp("created_at");
                        if (createdAt == null) continue;
                        String userId = rs.getString("union_openId");
                        messages.add(new MessageSample(
                                createdAt.toInstant(),
                                userId == null ? "" : userId,
                                countImages(rs.getString("attachments")),
                                isGameCommand(rs.getString("content"))
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.error("读取群聊天气数据失败: groupOpenId={}", groupOpenId, e);
            throw new IllegalStateException("读取群聊记录失败", e);
        }

        return analyze(messages, now, firstRecordedAt);
    }

    static WeatherReport analyze(List<MessageSample> allMessages, Instant now, Instant firstRecordedAt) {
        Instant currentStart = now.minus(CURRENT_WINDOW);
        List<MessageSample> current = allMessages.stream()
                .filter(message -> !message.time().isBefore(currentStart) && !message.time().isAfter(now))
                .sorted(Comparator.comparing(MessageSample::time))
                .toList();

        Instant observedStart = currentStart;
        if (firstRecordedAt != null && firstRecordedAt.isAfter(currentStart)) {
            observedStart = firstRecordedAt.isAfter(now) ? now.minusSeconds(60) : firstRecordedAt;
        }
        int windowMinutes = clamp((int) Math.max(1, Duration.between(observedStart, now).toMinutes()), 1, 24 * 60);

        WindowStats currentStats = stats(current, observedStart, now);
        List<WindowStats> baselines = baselineStats(allMessages, currentStart, firstRecordedAt);
        Baseline baseline = Baseline.from(baselines);

        Burst burst = burst(current, observedStart, windowMinutes);
        double rapidTurnRatio = rapidTurnRatio(current);
        WeatherType weather = chooseWeather(currentStats, baseline, burst);
        List<Phenomenon> phenomena = choosePhenomena(currentStats, baseline, rapidTurnRatio);

        int activityIndex;
        if (baseline.days() > 0 && baseline.messages() >= 1.0) {
            activityIndex = percentage(currentStats.messages() / baseline.messages() * 50.0);
        } else {
            activityIndex = clamp(currentStats.messages() * 2 + currentStats.activeUsers() * 5, 0, 100);
        }

        int imageIndex = currentStats.messages() == 0
                ? 0
                : percentage(currentStats.images() / (double) currentStats.messages() * 500.0);
        int nightIndex = currentStats.messages() == 0
                ? 0
                : percentage(currentStats.nightMessages() / (double) currentStats.messages() * 100.0);

        return new WeatherReport(
                now,
                windowMinutes,
                weather,
                phenomena,
                currentStats.messages(),
                currentStats.activeUsers(),
                currentStats.images(),
                currentStats.gameParticipants(),
                activityIndex,
                imageIndex,
                nightIndex,
                percentage(rapidTurnRatio * 100.0),
                baseline.days()
        );
    }

    private static List<WindowStats> baselineStats(List<MessageSample> allMessages, Instant currentStart,
                                                    Instant firstRecordedAt) {
        if (firstRecordedAt == null || !firstRecordedAt.isBefore(currentStart)) {
            return List.of();
        }
        List<WindowStats> result = new ArrayList<>();
        for (int day = 0; day < BASELINE_DAYS; day++) {
            Instant end = currentStart.minus(CURRENT_WINDOW.multipliedBy(day));
            Instant start = end.minus(CURRENT_WINDOW);
            Instant coveredStart = firstRecordedAt.isAfter(start) ? firstRecordedAt : start;
            long coveredMinutes = Duration.between(coveredStart, end).toMinutes();
            if (coveredMinutes < MIN_BASELINE_COVERAGE_MINUTES) continue;

            List<MessageSample> bucket = allMessages.stream()
                    .filter(message -> !message.time().isBefore(start) && message.time().isBefore(end))
                    .toList();
            result.add(stats(bucket, start, end));
        }
        return result;
    }

    private static WindowStats stats(List<MessageSample> messages, Instant start, Instant end) {
        Set<String> users = new HashSet<>();
        Set<String> gameUsers = new HashSet<>();
        int messageCount = 0;
        int images = 0;
        int nightMessages = 0;
        for (MessageSample message : messages) {
            if (message.time().isBefore(start) || message.time().isAfter(end)) continue;
            messageCount++;
            if (!message.userId().isBlank()) users.add(message.userId());
            if (message.gameCommand() && !message.userId().isBlank()) gameUsers.add(message.userId());
            images += message.imageCount();
            int hour = ZonedDateTime.ofInstant(message.time(), DISPLAY_ZONE).getHour();
            if (hour >= 0 && hour < 6) nightMessages++;
        }
        return new WindowStats(messageCount, users.size(), images, gameUsers.size(), nightMessages);
    }

    private static Burst burst(List<MessageSample> messages, Instant start, int windowMinutes) {
        if (messages.isEmpty()) return new Burst(0, 0.0);
        Map<Long, Integer> buckets = new HashMap<>();
        for (MessageSample message : messages) {
            long bucket = Math.max(0, Duration.between(start, message.time()).toMinutes()) / 15;
            buckets.merge(bucket, 1, Integer::sum);
        }
        int peak = buckets.values().stream().max(Integer::compareTo).orElse(0);
        double bucketCount = Math.max(1.0, windowMinutes / 15.0);
        double expected = Math.max(1.0, messages.size() / bucketCount);
        return new Burst(peak, peak / expected);
    }

    private static double rapidTurnRatio(List<MessageSample> messages) {
        int eligible = 0;
        int rapid = 0;
        for (int i = 1; i < messages.size(); i++) {
            MessageSample previous = messages.get(i - 1);
            MessageSample current = messages.get(i);
            if (previous.userId().isBlank() || current.userId().isBlank()) continue;
            long seconds = Duration.between(previous.time(), current.time()).toSeconds();
            if (seconds < 0 || seconds > 120) continue;
            eligible++;
            if (!previous.userId().equals(current.userId())) rapid++;
        }
        return eligible == 0 ? 0.0 : rapid / (double) eligible;
    }

    private static WeatherType chooseWeather(WindowStats current, Baseline baseline, Burst burst) {
        boolean thunderstorm = burst.peak() >= 6
                && (burst.intensity() >= 2.8 || burst.peak() >= Math.ceil(current.messages() * 0.35));
        if (thunderstorm) return WeatherType.THUNDERSTORM;

        if (baseline.days() > 0 && baseline.messages() >= 8.0
                && current.messages() < baseline.messages() * 0.45) {
            return WeatherType.WINDLESS_NIGHT;
        }
        if (baseline.days() == 0 && current.messages() <= 3 && current.activeUsers() <= 2) {
            return WeatherType.WINDLESS_NIGHT;
        }

        int activeThreshold = baseline.days() > 0
                ? Math.max(6, (int) Math.ceil(baseline.activeUsers() * 1.20))
                : 8;
        boolean stable = burst.peak() < Math.max(6, (int) Math.ceil(current.messages() * 0.25));
        if (current.activeUsers() >= activeThreshold && stable) {
            return WeatherType.CLOUDY;
        }
        return WeatherType.SUNNY;
    }

    private static List<Phenomenon> choosePhenomena(WindowStats current, Baseline baseline,
                                                     double rapidTurnRatio) {
        List<ScoredPhenomenon> candidates = new ArrayList<>();
        double imageShare = current.messages() == 0 ? 0.0 : current.images() / (double) current.messages();
        boolean imagesIncreased = baseline.days() == 0
                ? current.images() >= 4
                : current.images() >= Math.max(3.0, baseline.images() * 1.6);
        if (current.images() >= 3 && imageShare >= 0.15 && imagesIncreased) {
            candidates.add(new ScoredPhenomenon(Phenomenon.RAINBOW_CLOUD,
                    imageShare / 0.15 + current.images() / Math.max(4.0, baseline.images() * 1.6)));
        }
        if (current.gameParticipants() >= 3) {
            candidates.add(new ScoredPhenomenon(Phenomenon.METEOR_SHOWER,
                    current.gameParticipants() / 3.0));
        }
        double nightShare = current.messages() == 0
                ? 0.0
                : current.nightMessages() / (double) current.messages();
        if (current.nightMessages() >= 5 && nightShare >= 0.30) {
            candidates.add(new ScoredPhenomenon(Phenomenon.AURORA, nightShare / 0.30));
        }
        if (current.messages() >= 7 && current.activeUsers() >= 3 && rapidTurnRatio >= 0.55) {
            candidates.add(new ScoredPhenomenon(Phenomenon.PRESSURE_WAVE, rapidTurnRatio / 0.55));
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(ScoredPhenomenon::score).reversed()
                        .thenComparing(candidate -> candidate.phenomenon().name()))
                .limit(2)
                .map(ScoredPhenomenon::phenomenon)
                .toList();
    }

    private static int countImages(String rawAttachments) {
        if (rawAttachments == null || rawAttachments.isBlank()) return 0;
        try {
            JsonNode node = JSON.readTree(rawAttachments);
            if (!node.isArray()) return 0;
            int count = 0;
            for (JsonNode attachment : node) {
                String contentType = attachment.path("content_type").asText("");
                if (contentType.toLowerCase(Locale.ROOT).startsWith("image/")) count++;
            }
            return count;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean isGameCommand(String content) {
        return content != null && GAME_COMMAND.matcher(content.trim()).find();
    }

    private static int percentage(double value) {
        return clamp((int) Math.round(value), 0, 100);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record MessageSample(Instant time, String userId, int imageCount, boolean gameCommand) {
    }

    private record WindowStats(int messages, int activeUsers, int images, int gameParticipants,
                               int nightMessages) {
    }

    private record Baseline(int days, double messages, double activeUsers, double images) {
        private static Baseline from(List<WindowStats> values) {
            if (values.isEmpty()) return new Baseline(0, 0, 0, 0);
            return new Baseline(
                    values.size(),
                    values.stream().mapToInt(WindowStats::messages).average().orElse(0),
                    values.stream().mapToInt(WindowStats::activeUsers).average().orElse(0),
                    values.stream().mapToInt(WindowStats::images).average().orElse(0)
            );
        }
    }

    private record Burst(int peak, double intensity) {
    }

    private record ScoredPhenomenon(Phenomenon phenomenon, double score) {
    }
}
