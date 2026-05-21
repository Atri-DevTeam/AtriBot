package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * @Author YZ_Ljc_
 * @ClassName FormatTools
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.utils
 */
public class FormatTools {
    public static String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static String formatTimestamp(String timestamp) {
        long timestampLong = Long.parseLong(timestamp);
        return formatTimestamp(timestampLong);
    }

    public static String formatTimestampMilli(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static Timestamp parseTimestamp(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode node = root.get(key);
            if (node == null || node.isNull()) {
                continue;
            }

            if (node.isNumber()) {
                return new Timestamp(node.asLong());
            }

            String value = node.asText("");
            if (value.isBlank()) {
                continue;
            }

            try {
                return Timestamp.valueOf(value);
            } catch (IllegalArgumentException ignored) {
            }

            try {
                return Timestamp.from(Instant.parse(value));
            } catch (DateTimeParseException ignored) {
            }

            try {
                return Timestamp.from(OffsetDateTime.parse(value).toInstant());
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}