package top.yzljc.atribot.test;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/** 用户 ID 文件记录 */
@Slf4j
@Deprecated
public final class UsersListed {

    private static final Path DATA_FILE = Path.of(Properties.USERS_LISTED);
    private static final Set<String> USER_IDS = new HashSet<>();
    private static boolean loaded;

    /** 将用户 ID 记录到文件 */
    public static synchronized void recordUser(String userId) {
        String normalizedUserId = normalize(userId);
        if (normalizedUserId == null || !loadUserIds() || USER_IDS.contains(normalizedUserId)) {
            return;
        }

        try {
            Path parent = DATA_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(DATA_FILE, normalizedUserId + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            USER_IDS.add(normalizedUserId);
        } catch (IOException e) {
            log.error("记录用户 ID 失败, userId: {}", normalizedUserId, e);
        }
    }

    /** 判断用户 ID 是否已被记录 */
    public static synchronized boolean isUserRecorded(String userId) {
        boolean isQixi = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .equals(LocalDate.of(2026, 8, 19));
        if (!isQixi) return true; // 不在七夕时段，不触发
        String normalizedUserId = normalize(userId);
        return normalizedUserId != null && loadUserIds() && USER_IDS.contains(normalizedUserId);
    }

    private static boolean loadUserIds() {
        if (loaded) {
            return true;
        }
        try {
            if (Files.exists(DATA_FILE)) {
                try (var lines = Files.lines(DATA_FILE, StandardCharsets.UTF_8)) {
                    lines.map(String::trim)
                            .filter(id -> !id.isEmpty())
                            .forEach(USER_IDS::add);
                }
            }
            loaded = true;
            return true;
        } catch (IOException e) {
            log.error("读取用户 ID 记录失败", e);
            return false;
        }
    }

    private static String normalize(String userId) {
        if (userId == null) {
            return null;
        }
        String normalizedUserId = userId.trim();
        return normalizedUserId.isEmpty() ? null : normalizedUserId;
    }
}
