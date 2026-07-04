package top.yzljc.atribot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.text.StringEscapeUtils;

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
        long timestampLong;
        try {
            timestampLong = Long.parseLong(timestamp);
        } catch (Exception _) {
            return "-";
        }
        return formatTimestamp(timestampLong);
    }

    public static String formatTimestampMilli(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static String unescape(String text) {
        if (text == null) return null;
        return StringEscapeUtils.unescapeHtml4(text);
    }

    public static String formatIsoTime(String isoTime) {
        return formatIsoTime(isoTime, "yyyy-MM-dd HH:mm:ss");
    }

    public static String formatIsoTime(String isoTime, String pattern) {
        try {
            return OffsetDateTime.parse(isoTime)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            return isoTime;
        }
    }

    private static final DateTimeFormatter MOJIRA_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public static String formatMojiraTime(String mojiraTime) {
        return formatMojiraTime(mojiraTime, "yyyy-MM-dd HH:mm:ss");
    }

    public static String formatMojiraTime(String mojiraTime, String pattern) {
        if (mojiraTime == null || mojiraTime.isBlank()) {
            return "";
        }

        try {
            return OffsetDateTime.parse(mojiraTime, MOJIRA_TIME_FORMATTER)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            return mojiraTime;
        }
    }
}