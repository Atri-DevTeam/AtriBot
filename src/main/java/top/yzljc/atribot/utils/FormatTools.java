package top.yzljc.atribot.utils;

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
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils
 */
public class FormatTools {
    private static final DateTimeFormatter DEFAULT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 按 yyyy-MM-dd HH:mm:ss 格式化数据库时间，沿用 {@link Timestamp#toLocalDateTime()} 的本地时间语义
     *
     * @param timestamp 数据库时间，可为 null
     * @param fallback 时间为 null 时返回的值，可为 null
     * @return 格式化结果或 fallback
     */
    public static String formatTimestamp(Timestamp timestamp, String fallback) {
        return formatTimestamp(timestamp, DEFAULT_TIME_FORMAT, fallback);
    }

    /**
     * 使用指定格式格式化数据库时间，沿用 {@link Timestamp#toLocalDateTime()} 的本地时间语义
     *
     * @param timestamp 数据库时间，可为 null
     * @param formatter 时间格式
     * @param fallback 时间为 null 时返回的值，可为 null
     * @return 格式化结果或 fallback
     */
    public static String formatTimestamp(Timestamp timestamp, DateTimeFormatter formatter, String fallback) {
        return timestamp == null ? fallback : timestamp.toLocalDateTime().format(formatter);
    }

    public static String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
        );
        return dateTime.format(DEFAULT_TIME_FORMAT);
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
        return dateTime.format(DEFAULT_TIME_FORMAT);
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