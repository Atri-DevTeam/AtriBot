package top.yzljc.qqbot.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
}