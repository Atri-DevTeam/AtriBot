package top.yzljc.atribot.webui.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @Author YZ_Ljc_
 * @ClassName ChatStatsSnapshotStore
 * @Created_at 2026/08/14
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.impl
 */
@Slf4j
public final class ChatStatsSnapshotRepo {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GROUP_TABLE = "official_group_record";
    private static final String C2C_TABLE = "official_c2c_record";
    private static final String SNAPSHOT_TABLE = "official_chat_stats_snapshot";
    private static final String BOT_SEND = "BOT_SEND";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Snapshot snapshot;

    private ChatStatsSnapshotRepo() {
    }

    public static synchronized void ensureInitialized() {
        if (snapshot != null) return;
        ensureTable();
        if (loadFromDatabase()) return;
        snapshot = new Snapshot();
        rebuildFromDatabase();
        persistAll();
    }

    /** 在批量删除消息前，将当前完整统计快照强制落库。 */
    public static synchronized void archiveCurrentSnapshot() {
        ensureInitialized();
        if (!persistAll()) {
            throw new IllegalStateException("统计快照归档失败，已中止聊天记录清理");
        }
    }

    private static boolean loadFromDatabase() {
        Snapshot loaded = new Snapshot();
        boolean found = false;
        String sql = "SELECT scope_key, scope_type, scope_id, stat_date, n1, n2, n3, n4, n5, " +
                "json_a, json_b, json_c, json_d, first_seen_at, last_seen_at, last_username " +
                "FROM `" + SNAPSHOT_TABLE + "`";
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql)) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                found = true;
                String type = rs.getString("scope_type");
                String id = rs.getString("scope_id");
                java.sql.Date dateValue = rs.getDate("stat_date");
                String date = dateValue == null ? null : dateValue.toLocalDate().toString();
                long n1 = rs.getLong("n1"), n2 = rs.getLong("n2"), n3 = rs.getLong("n3"),
                        n4 = rs.getLong("n4"), n5 = rs.getLong("n5");
                if ("global".equals(type) && date != null) {
                    DailyStats d = new DailyStats();
                    d.groupReceived = n1; d.groupSent = n2; d.c2cReceived = n3; d.c2cSent = n4;
                    d.receiveUsers = readSet(rs.getString("json_a"));
                    d.sendGroups = readSet(rs.getString("json_b"));
                    d.c2cReceiveUsers = readSet(rs.getString("json_c"));
                    d.c2cSendUsers = readSet(rs.getString("json_d"));
                    loaded.days.put(date, d);
                } else if (("group".equals(type) || "user".equals(type)) && id != null) {
                    ScopeStats s = "group".equals(type)
                            ? loaded.groups.computeIfAbsent(id, k -> new ScopeStats())
                            : loaded.users.computeIfAbsent(id, k -> new ScopeStats());
                    if (date == null) {
                        s.received = n1; s.sent = n2; s.c2cReceived = n3; s.c2cSent = n4; s.groupReceived = n5;
                        s.activeUsers = readSet(rs.getString("json_a"));
                        s.firstSeenAt = rs.getString("first_seen_at");
                        s.lastSeenAt = rs.getString("last_seen_at");
                        s.lastUsername = rs.getString("last_username");
                    } else {
                        s.daily.put(date, new long[]{n1, n2});
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("读取聊天统计数据库快照失败，将从消息表重建: {}", e.getMessage());
            return false;
        }
        if (!found) return false;
        normalize(loaded);
        snapshot = loaded;
        return true;
    }

    public static synchronized void recordGroupMessage(String groupOpenId, String unionOpenId,
                                                        String username, boolean senderIsBot,
                                                        String eventTimestamp) {
        ensureInitialized();
        addGroupMessage(snapshot, groupOpenId, unionOpenId, username, senderIsBot, eventTimestamp);
        persistIncremental(groupOpenId, unionOpenId, dayOf(eventTimestamp));
    }

    public static synchronized void recordC2CMessage(String userOpenId, String username,
                                                      boolean senderIsBot, String source,
                                                      String eventTimestamp) {
        ensureInitialized();
        addC2CMessage(snapshot, userOpenId, username, senderIsBot || BOT_SEND.equals(source), eventTimestamp);
        persistIncremental(null, userOpenId, dayOf(eventTimestamp));
    }

    public static synchronized long countGroupMessages(boolean botSent, LocalDateTime start,
                                                       LocalDateTime end, String groupOpenId) {
        ensureInitialized();
        if (isBlank(groupOpenId)) return sumGlobal(botSent ? 1 : 0, start, end);
        ScopeStats stats = snapshot.groups.get(groupOpenId);
        return stats == null ? 0 : sumDaily(stats.daily, botSent ? 1 : 0, start, end);
    }

    public static synchronized long countC2CMessages(boolean botSent, LocalDateTime start,
                                                     LocalDateTime end, String userOpenId) {
        ensureInitialized();
        if (isBlank(userOpenId)) return sumGlobal(botSent ? 3 : 2, start, end);
        ScopeStats stats = snapshot.users.get(userOpenId);
        return stats == null ? 0 : sumDaily(stats.daily, botSent ? 1 : 0, start, end);
    }

    public static synchronized DauStats queryDau(LocalDateTime start, LocalDateTime end,
                                                  String groupOpenId, String userOpenId) {
        ensureInitialized();
        Set<String> receiveUsers = new LinkedHashSet<>();
        Set<String> sendGroups = new LinkedHashSet<>();
        Set<String> c2cReceiveUsers = new LinkedHashSet<>();
        Set<String> c2cSendUsers = new LinkedHashSet<>();
        for (Map.Entry<String, DailyStats> entry : snapshot.days.entrySet()) {
            if (!inRange(entry.getKey(), start, end)) continue;
            DailyStats day = entry.getValue();
            receiveUsers.addAll(day.receiveUsers);
            sendGroups.addAll(day.sendGroups);
            c2cReceiveUsers.addAll(day.c2cReceiveUsers);
            c2cSendUsers.addAll(day.c2cSendUsers);
        }
        if (!isBlank(groupOpenId)) {
            ScopeStats group = snapshot.groups.get(groupOpenId);
            return new DauStats(group == null ? 0 : group.activeUsers.size(),
                    group == null ? 0 : (group.sent == 0 ? 0 : 1),
                    isBlank(userOpenId) ? 0 : c2cReceiveUsers.contains(userOpenId) ? 1 : 0,
                    isBlank(userOpenId) ? 0 : c2cSendUsers.contains(userOpenId) ? 1 : 0,
                    receiveUsers.size());
        }
        if (!isBlank(userOpenId)) {
            return new DauStats(receiveUsers.size(), sendGroups.size(),
                    c2cReceiveUsers.contains(userOpenId) ? 1 : 0,
                    c2cSendUsers.contains(userOpenId) ? 1 : 0, receiveUsers.size());
        }
        return new DauStats(receiveUsers.size(), sendGroups.size(), c2cReceiveUsers.size(),
                c2cSendUsers.size(), receiveUsers.size());
    }

    public static synchronized double averageDailyDau() {
        ensureInitialized();
        long total = 0;
        int days = 0;
        for (DailyStats day : snapshot.days.values()) {
            if (!day.receiveUsers.isEmpty()) {
                total += day.receiveUsers.size();
                days++;
            }
        }
        return days == 0 ? 0 : (double) total / days;
    }

    public static synchronized List<DailyStatsView> dailySeries(LocalDateTime start, LocalDateTime end) {
        ensureInitialized();
        List<DailyStatsView> result = new ArrayList<>();
        for (Map.Entry<String, DailyStats> entry : snapshot.days.entrySet()) {
            if (!inRange(entry.getKey(), start, end)) continue;
            DailyStats day = entry.getValue();
            if (day.groupReceived + day.groupSent + day.c2cReceived + day.c2cSent == 0
                    && day.receiveUsers.isEmpty()) continue;
            result.add(new DailyStatsView(entry.getKey(), day.groupReceived, day.groupSent,
                    day.c2cReceived, day.c2cSent, day.receiveUsers.size()));
        }
        return result;
    }

    public static synchronized GroupSummary groupSummary(String groupOpenId) {
        ensureInitialized();
        ScopeStats stats = snapshot.groups.get(groupOpenId);
        if (stats == null) return new GroupSummary(0, 0, 0, null, null);
        return new GroupSummary(stats.received, stats.sent, stats.activeUsers.size(), stats.firstSeenAt, stats.lastSeenAt);
    }

    public static synchronized UserSummary userSummary(String userOpenId) {
        ensureInitialized();
        ScopeStats stats = snapshot.users.get(userOpenId);
        if (stats == null) return new UserSummary(0, 0, 0, null, null, null);
        return new UserSummary(stats.c2cReceived, stats.c2cSent, stats.groupReceived,
                stats.firstSeenAt, stats.lastSeenAt, stats.lastUsername);
    }

    private static void rebuildFromDatabase() {
        String groupSql = "SELECT group_openId, union_openId, username, sender_is_bot, event_type, "
                + "COALESCE(STR_TO_DATE(REPLACE(SUBSTRING(event_timestamp, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), created_at) event_time "
                + "FROM `" + GROUP_TABLE + "`";
        String c2cSql = "SELECT union_openId, username, sender_is_bot, source, "
                + "COALESCE(STR_TO_DATE(REPLACE(SUBSTRING(event_timestamp, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), created_at) event_time "
                + "FROM `" + C2C_TABLE + "`";
        try (var conn = DatabaseManager.getConnection();
             var groupStmt = conn.prepareStatement(groupSql);
             var c2cStmt = conn.prepareStatement(c2cSql)) {
            try (var rs = groupStmt.executeQuery()) {
                while (rs.next()) {
                    addGroupMessage(snapshot, rs.getString("group_openId"), rs.getString("union_openId"),
                            rs.getString("username"), rs.getBoolean("sender_is_bot") || BOT_SEND.equals(rs.getString("event_type")),
                            formatTime(rs.getTimestamp("event_time")));
                }
            }
            try (var rs = c2cStmt.executeQuery()) {
                while (rs.next()) {
                    addC2CMessage(snapshot, rs.getString("union_openId"), rs.getString("username"),
                            rs.getBoolean("sender_is_bot") || BOT_SEND.equals(rs.getString("source")),
                            formatTime(rs.getTimestamp("event_time")));
                }
            }
        } catch (SQLException e) {
            log.error("建立聊天统计快照失败: {}", e.getMessage(), e);
        }
    }

    private static void addGroupMessage(Snapshot data, String groupOpenId, String unionOpenId,
                                        String username, boolean senderIsBot, String timestamp) {
        if (isBlank(groupOpenId)) return;
        String day = dayOf(timestamp);
        DailyStats daily = data.days.computeIfAbsent(day, ignored -> new DailyStats());
        ScopeStats group = data.groups.computeIfAbsent(groupOpenId, ignored -> new ScopeStats());
        addScopeDaily(group, day, senderIsBot);
        if (senderIsBot) {
            daily.groupSent++;
            daily.sendGroups.add(groupOpenId);
            group.sent++;
        } else {
            daily.groupReceived++;
            if (!isBlank(unionOpenId)) {
                daily.receiveUsers.add(unionOpenId);
                group.activeUsers.add(unionOpenId);
            }
            group.received++;
        }
        updateSeen(group, timestamp);
        if (!senderIsBot && !isBlank(unionOpenId)) {
            ScopeStats user = data.users.computeIfAbsent(unionOpenId, ignored -> new ScopeStats());
            user.groupReceived++;
            user.lastUsername = isBlank(username) ? user.lastUsername : username;
            updateSeen(user, timestamp);
        }
    }

    private static void addC2CMessage(Snapshot data, String userOpenId, String username,
                                      boolean senderIsBot, String timestamp) {
        if (isBlank(userOpenId)) return;
        String day = dayOf(timestamp);
        DailyStats daily = data.days.computeIfAbsent(day, ignored -> new DailyStats());
        ScopeStats user = data.users.computeIfAbsent(userOpenId, ignored -> new ScopeStats());
        addScopeDaily(user, day, senderIsBot);
        if (senderIsBot) {
            daily.c2cSent++;
            daily.c2cSendUsers.add(userOpenId);
            user.c2cSent++;
        } else {
            daily.c2cReceived++;
            daily.receiveUsers.add(userOpenId);
            daily.c2cReceiveUsers.add(userOpenId);
            user.c2cReceived++;
        }
        if (!senderIsBot && !isBlank(username)) user.lastUsername = username;
        updateSeen(user, timestamp);
    }

    private static void addScopeDaily(ScopeStats stats, String day, boolean senderIsBot) {
        long[] counts = stats.daily.computeIfAbsent(day, ignored -> new long[2]);
        counts[senderIsBot ? 1 : 0]++;
    }

    private static void updateSeen(ScopeStats stats, String timestamp) {
        String value = normalizeTime(timestamp);
        if (stats.firstSeenAt == null || value.compareTo(stats.firstSeenAt) < 0) stats.firstSeenAt = value;
        if (stats.lastSeenAt == null || value.compareTo(stats.lastSeenAt) > 0) stats.lastSeenAt = value;
    }

    private static long sumGlobal(int index, LocalDateTime start, LocalDateTime end) {
        long total = 0;
        for (Map.Entry<String, DailyStats> entry : snapshot.days.entrySet()) {
            if (!inRange(entry.getKey(), start, end)) continue;
            DailyStats d = entry.getValue();
            total += index == 0 ? d.groupReceived : index == 1 ? d.groupSent : index == 2 ? d.c2cReceived : d.c2cSent;
        }
        return total;
    }

    private static long sumDaily(Map<String, long[]> daily, int index, LocalDateTime start, LocalDateTime end) {
        long total = 0;
        for (Map.Entry<String, long[]> entry : daily.entrySet()) {
            if (inRange(entry.getKey(), start, end)) total += entry.getValue()[index];
        }
        return total;
    }

    private static boolean inRange(String day, LocalDateTime start, LocalDateTime end) {
        LocalDate date = LocalDate.parse(day);
        return (start == null || !date.isBefore(start.toLocalDate()))
                && (end == null || date.isBefore(end.toLocalDate()));
    }

    private static String dayOf(String timestamp) {
        String value = normalizeTime(timestamp);
        return value.length() >= 10 ? value.substring(0, 10) : LocalDate.now().toString();
    }

    private static String normalizeTime(String timestamp) {
        if (isBlank(timestamp)) return LocalDateTime.now().format(TIME_FORMAT);
        return timestamp.trim().replace('T', ' ');
    }

    private static String formatTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(TIME_FORMAT);
    }

    private static void normalize(Snapshot data) {
        if (data.days == null) data.days = new TreeMap<>();
        else data.days = new TreeMap<>(data.days);
        if (data.groups == null) data.groups = new LinkedHashMap<>();
        if (data.users == null) data.users = new LinkedHashMap<>();
        data.days.values().forEach(DailyStats::normalize);
        data.groups.values().forEach(stats -> { stats.normalize(); stats.daily = new TreeMap<>(stats.daily); });
        data.users.values().forEach(stats -> { stats.normalize(); stats.daily = new TreeMap<>(stats.daily); });
    }

    private static void ensureTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `" + SNAPSHOT_TABLE + "` (" +
                "  `scope_key` VARCHAR(768) NOT NULL," +
                "  `scope_type` VARCHAR(16) NOT NULL," +
                "  `scope_id` VARCHAR(512) NULL," +
                "  `stat_date` DATE NULL," +
                "  `n1` BIGINT NOT NULL DEFAULT 0, `n2` BIGINT NOT NULL DEFAULT 0," +
                "  `n3` BIGINT NOT NULL DEFAULT 0, `n4` BIGINT NOT NULL DEFAULT 0," +
                "  `n5` BIGINT NOT NULL DEFAULT 0," +
                "  `json_a` MEDIUMTEXT NULL, `json_b` MEDIUMTEXT NULL," +
                "  `json_c` MEDIUMTEXT NULL, `json_d` MEDIUMTEXT NULL," +
                "  `first_seen_at` VARCHAR(32) NULL, `last_seen_at` VARCHAR(32) NULL," +
                "  `last_username` VARCHAR(256) NULL," +
                "  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`scope_key`), INDEX `idx_scope_date` (`scope_type`, `scope_id`, `stat_date`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            log.error("初始化聊天统计快照表失败: {}", e.getMessage(), e);
        }
    }

    private static boolean persistAll() {
        ensureTable();
        String sql = "INSERT INTO `" + SNAPSHOT_TABLE + "` " +
                "(scope_key, scope_type, scope_id, stat_date, n1, n2, n3, n4, n5, json_a, json_b, json_c, json_d, first_seen_at, last_seen_at, last_username) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE scope_type=VALUES(scope_type), scope_id=VALUES(scope_id), stat_date=VALUES(stat_date), " +
                "n1=VALUES(n1), n2=VALUES(n2), n3=VALUES(n3), n4=VALUES(n4), n5=VALUES(n5), " +
                "json_a=VALUES(json_a), json_b=VALUES(json_b), json_c=VALUES(json_c), json_d=VALUES(json_d), " +
                "first_seen_at=VALUES(first_seen_at), last_seen_at=VALUES(last_seen_at), last_username=VALUES(last_username)";
        try (var conn = DatabaseManager.getConnection(); var delete = conn.createStatement(); var stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            delete.executeUpdate("DELETE FROM `" + SNAPSHOT_TABLE + "`");
            for (Map.Entry<String, DailyStats> entry : snapshot.days.entrySet()) {
                DailyStats d = entry.getValue();
                bind(stmt, "global:" + entry.getKey(), "global", null, entry.getKey(), d.groupReceived, d.groupSent,
                        d.c2cReceived, d.c2cSent, 0, d.receiveUsers, d.sendGroups, d.c2cReceiveUsers, d.c2cSendUsers, null, null, null);
                stmt.addBatch();
            }
            for (Map.Entry<String, ScopeStats> entry : snapshot.groups.entrySet()) {
                persistScope(stmt, "group", entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, ScopeStats> entry : snapshot.users.entrySet()) {
                persistScope(stmt, "user", entry.getKey(), entry.getValue());
            }
            stmt.executeBatch();
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("保存聊天统计数据库快照失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private static void persistScope(java.sql.PreparedStatement stmt, String type, String id, ScopeStats s) throws SQLException {
        bind(stmt, type + ":" + id + ":summary", type, id, null, s.received, s.sent, s.c2cReceived, s.c2cSent,
                s.groupReceived, s.activeUsers, null, null, null, s.firstSeenAt, s.lastSeenAt, s.lastUsername);
        stmt.addBatch();
        for (Map.Entry<String, long[]> daily : s.daily.entrySet()) {
            long[] counts = daily.getValue();
            bind(stmt, type + ":" + id + ":" + daily.getKey(), type, id, daily.getKey(), counts[0], counts[1], 0, 0, 0,
                    null, null, null, null, null, null, null);
            stmt.addBatch();
        }
    }

    private static void persistIncremental(String groupOpenId, String userOpenId, String day) {
        String sql = upsertSql();
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            DailyStats global = snapshot.days.get(day);
            if (global != null) {
                bind(stmt, "global:" + day, "global", null, day, global.groupReceived, global.groupSent,
                        global.c2cReceived, global.c2cSent, 0, global.receiveUsers, global.sendGroups,
                        global.c2cReceiveUsers, global.c2cSendUsers, null, null, null);
                stmt.addBatch();
            }
            if (!isBlank(groupOpenId)) {
                ScopeStats group = snapshot.groups.get(groupOpenId);
                if (group != null) persistScopeRows(stmt, "group", groupOpenId, group, day);
            }
            if (!isBlank(userOpenId)) {
                ScopeStats user = snapshot.users.get(userOpenId);
                if (user != null) persistScopeRows(stmt, "user", userOpenId, user, day);
            }
            stmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            log.error("增量保存聊天统计失败: {}", e.getMessage(), e);
        }
    }

    private static void persistScopeRows(java.sql.PreparedStatement stmt, String type, String id,
                                          ScopeStats s, String day) throws SQLException {
        bind(stmt, type + ":" + id + ":summary", type, id, null, s.received, s.sent, s.c2cReceived,
                s.c2cSent, s.groupReceived, s.activeUsers, null, null, null, s.firstSeenAt,
                s.lastSeenAt, s.lastUsername);
        stmt.addBatch();
        long[] counts = s.daily.get(day);
        if (counts != null) {
            bind(stmt, type + ":" + id + ":" + day, type, id, day, counts[0], counts[1], 0, 0, 0,
                    null, null, null, null, null, null, null);
            stmt.addBatch();
        }
    }

    private static String upsertSql() {
        return "INSERT INTO `" + SNAPSHOT_TABLE + "` " +
                "(scope_key, scope_type, scope_id, stat_date, n1, n2, n3, n4, n5, json_a, json_b, json_c, json_d, first_seen_at, last_seen_at, last_username) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE scope_type=VALUES(scope_type), scope_id=VALUES(scope_id), stat_date=VALUES(stat_date), " +
                "n1=VALUES(n1), n2=VALUES(n2), n3=VALUES(n3), n4=VALUES(n4), n5=VALUES(n5), " +
                "json_a=VALUES(json_a), json_b=VALUES(json_b), json_c=VALUES(json_c), json_d=VALUES(json_d), " +
                "first_seen_at=VALUES(first_seen_at), last_seen_at=VALUES(last_seen_at), last_username=VALUES(last_username)";
    }

    private static void bind(java.sql.PreparedStatement stmt, String key, String type, String id, String date,
                             long n1, long n2, long n3, long n4, long n5, Set<String> a, Set<String> b,
                             Set<String> c, Set<String> d, String first, String last, String username) throws SQLException {
        stmt.setString(1, key); stmt.setString(2, type); stmt.setString(3, id);
        if (date == null) stmt.setNull(4, java.sql.Types.DATE); else stmt.setDate(4, java.sql.Date.valueOf(date));
        stmt.setLong(5, n1); stmt.setLong(6, n2); stmt.setLong(7, n3); stmt.setLong(8, n4); stmt.setLong(9, n5);
        stmt.setString(10, writeSet(a)); stmt.setString(11, writeSet(b)); stmt.setString(12, writeSet(c)); stmt.setString(13, writeSet(d));
        stmt.setString(14, first); stmt.setString(15, last); stmt.setString(16, username);
    }

    private static String writeSet(Set<String> values) throws SQLException {
        if (values == null || values.isEmpty()) return null;
        try { return MAPPER.writeValueAsString(values); }
        catch (IOException e) { throw new SQLException("序列化统计去重集合失败", e); }
    }

    private static Set<String> readSet(String json) {
        if (json == null || json.isBlank()) return new LinkedHashSet<>();
        try { return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(LinkedHashSet.class, String.class)); }
        catch (IOException e) { log.warn("解析统计去重集合失败: {}", e.getMessage()); return new LinkedHashSet<>(); }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record DauStats(long groupReceiveUsers, long groupSendGroups, long c2cReceiveUsers,
                           long c2cSendUsers, long totalReceiveUsers) {}

    public record DailyStatsView(String date, long groupReceived, long groupSent,
                                 long c2cReceived, long c2cSent, long dau) {}

    public record GroupSummary(long received, long sent, long activeUsers,
                               String firstSeenAt, String lastSeenAt) {}

    public record UserSummary(long c2cReceived, long c2cSent, long groupReceived,
                              String firstSeenAt, String lastSeenAt, String lastUsername) {}

    public static class Snapshot {
        public Map<String, DailyStats> days = new TreeMap<>();
        public Map<String, ScopeStats> groups = new LinkedHashMap<>();
        public Map<String, ScopeStats> users = new LinkedHashMap<>();
    }

    public static class DailyStats {
        public long groupReceived;
        public long groupSent;
        public long c2cReceived;
        public long c2cSent;
        public Set<String> receiveUsers = new LinkedHashSet<>();
        public Set<String> sendGroups = new LinkedHashSet<>();
        public Set<String> c2cReceiveUsers = new LinkedHashSet<>();
        public Set<String> c2cSendUsers = new LinkedHashSet<>();

        public void normalize() {
            if (receiveUsers == null) receiveUsers = new LinkedHashSet<>();
            if (sendGroups == null) sendGroups = new LinkedHashSet<>();
            if (c2cReceiveUsers == null) c2cReceiveUsers = new LinkedHashSet<>();
            if (c2cSendUsers == null) c2cSendUsers = new LinkedHashSet<>();
        }
    }

    public static class ScopeStats {
        public long received;
        public long sent;
        public long c2cReceived;
        public long c2cSent;
        public long groupReceived;
        public Set<String> activeUsers = new LinkedHashSet<>();
        public String firstSeenAt;
        public String lastSeenAt;
        public String lastUsername;
        public Map<String, long[]> daily = new TreeMap<>();

        public void normalize() {
            if (activeUsers == null) activeUsers = new LinkedHashSet<>();
            if (daily == null) daily = new TreeMap<>();
        }
    }

}
