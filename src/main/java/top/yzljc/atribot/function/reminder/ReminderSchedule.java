package top.yzljc.atribot.function.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName ReminderSchedule
 * @Created_at 2026/09/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.reminder
 */
public record ReminderSchedule(Kind kind, LocalDateTime at, LocalTime time,
                               List<Integer> weekdays, Integer day, Integer intervalMinutes) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Kind { ONCE, DAILY, WEEKLY, MONTHLY, INTERVAL }

    public ReminderSchedule {
        if (kind == null) throw new IllegalArgumentException("Missing schedule kind");
        weekdays = weekdays == null ? List.of() : List.copyOf(weekdays);
        boolean calendar = kind == Kind.DAILY || kind == Kind.WEEKLY || kind == Kind.MONTHLY;
        if ((kind == Kind.ONCE) == (at == null) || calendar == (time == null)
                || (kind == Kind.WEEKLY) == weekdays.isEmpty()
                || (kind == Kind.MONTHLY) == (day == null)
                || (kind == Kind.INTERVAL) == (intervalMinutes == null)) {
            throw new IllegalArgumentException("Unexpected schedule fields");
        }
        if (time != null && (time.getSecond() != 0 || time.getNano() != 0)) {
            throw new IllegalArgumentException("Only minute precision is supported");
        }
        if (weekdays.size() > 7 || weekdays.stream().anyMatch(n -> n < 1 || n > 7)
                || weekdays.stream().distinct().count() != weekdays.size()
                || (day != null && (day < 1 || day > 31))
                || (intervalMinutes != null && (intervalMinutes < 10 || intervalMinutes > 525_600))) {
            throw new IllegalArgumentException("Invalid recurrence");
        }
    }

    public static ReminderSchedule fromJson(JsonNode node) {
        if (node == null || !node.isObject() || !node.path("kind").isTextual()) {
            throw new IllegalArgumentException("Invalid schedule");
        }
        Kind kind = Kind.valueOf(node.path("kind").textValue());
        Set<String> fields = switch (kind) {
            case ONCE -> Set.of("kind", "at");
            case DAILY -> Set.of("kind", "time");
            case WEEKLY -> Set.of("kind", "time", "weekdays");
            case MONTHLY -> Set.of("kind", "time", "day");
            case INTERVAL -> Set.of("kind", "interval_minutes");
        };
        if (node.size() != fields.size()) throw new IllegalArgumentException("Unexpected schedule fields");
        node.fieldNames().forEachRemaining(name -> {
            if (!fields.contains(name)) throw new IllegalArgumentException("Unexpected schedule field");
        });
        List<Integer> weekdays = new ArrayList<>();
        if (kind == Kind.WEEKLY) {
            if (!node.path("weekdays").isArray()) throw new IllegalArgumentException("Invalid weekdays");
            for (JsonNode value : node.path("weekdays")) weekdays.add(integer(value));
        }
        return new ReminderSchedule(kind,
                kind == Kind.ONCE ? LocalDateTime.parse(text(node.path("at"))) : null,
                fields.contains("time") ? LocalTime.parse(text(node.path("time")), TIME) : null,
                weekdays, kind == Kind.MONTHLY ? integer(node.path("day")) : null,
                kind == Kind.INTERVAL ? integer(node.path("interval_minutes")) : null);
    }

    public String toJson() {
        ObjectNode node = MAPPER.createObjectNode().put("kind", kind.name());
        switch (kind) {
            case ONCE -> node.put("at", at.toString());
            case DAILY -> node.put("time", time.format(TIME));
            case WEEKLY -> {
                node.put("time", time.format(TIME));
                weekdays.forEach(node.putArray("weekdays")::add);
            }
            case MONTHLY -> node.put("time", time.format(TIME)).put("day", day);
            case INTERVAL -> node.put("interval_minutes", intervalMinutes);
        }
        return node.toString();
    }

    /** interval 的 anchor 是原定触发时刻；重启后跳过错过的周期，不连续补发。 */
    public Instant nextAfter(Instant now, Instant anchor) {
        if (kind == Kind.ONCE) {
            Instant result = at.atZone(ZONE).toInstant();
            return result.isAfter(now) ? result : null;
        }
        if (kind == Kind.INTERVAL) {
            Duration period = Duration.ofMinutes(intervalMinutes);
            if (anchor == null) return now.plus(period);
            if (anchor.isAfter(now)) return anchor;
            long steps = Duration.between(anchor, now).toMillis() / period.toMillis() + 1;
            return anchor.plus(period.multipliedBy(steps));
        }
        LocalDate date = now.atZone(ZONE).toLocalDate();
        // 最长等待跨过一个短月份：31 日任务跳过没有 31 日的月份。
        for (int offset = 0; offset <= 62; offset++) {
            LocalDate candidate = date.plusDays(offset);
            if (kind == Kind.WEEKLY && !weekdays.contains(candidate.getDayOfWeek().getValue())) continue;
            if (kind == Kind.MONTHLY && candidate.getDayOfMonth() != day) continue;
            Instant result = candidate.atTime(time).atZone(ZONE).toInstant();
            if (result.isAfter(now)) return result;
        }
        throw new IllegalStateException("Unable to calculate next reminder");
    }

    private static String text(JsonNode value) {
        if (!value.isTextual() || value.textValue().length() > 32) {
            throw new IllegalArgumentException("Invalid time field");
        }
        return value.textValue();
    }

    private static int integer(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Invalid integer field");
        }
        return value.intValue();
    }
}
