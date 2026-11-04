package top.yzljc.atribot.database.repo;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.function.games.door.DailyDoorRules;

/**
 * @Author YZ_Ljc_
 * @ClassName DailyDoorRepository
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public final class DailyDoorRepository {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger LOG = LoggerFactory.getLogger(DailyDoorRepository.class);
    private static final int OPEN_REWARD = 50;
    private final UserGameDataRepository.Connections connections;
    private final Clock clock;
    private final RandomGenerator random;

    public enum Result {VIEW, NORMAL_FAIL, RARE_SOUND, RARE_MOVEMENT, RARE_LIGHT, SUCCESS, ALREADY_ATTEMPTED, ALREADY_OPEN}

    public record Door(LocalDate date, long number, long attempts, LocalDateTime openedAt, String ending) {
        public boolean open() {
            return openedAt != null;
        }
    }

    public record View(Door today, Door yesterday, boolean attempted, Result result, String feedback) {
    }

    private record StoredDoor(Door door, double referenceVisitors) {
    }

    public DailyDoorRepository() {
        this(DatabaseManager::getConnection, Clock.system(ZONE), ThreadLocalRandom.current());
    }

    public DailyDoorRepository(UserGameDataRepository.Connections connections, Clock clock, RandomGenerator random) {
        this.connections = connections;
        this.clock = clock.withZone(ZONE);
        this.random = random;
    }

    public void init() throws SQLException {
        try (var con = connections.open(); var statement = con.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS daily_door (
                      door_date DATE NOT NULL PRIMARY KEY,
                      door_number BIGINT NOT NULL,
                      attempt_count BIGINT NOT NULL DEFAULT 0,
                      visitor_count BIGINT NOT NULL DEFAULT 0,
                      reference_visitors DOUBLE NOT NULL DEFAULT 0,
                      opened_at DATETIME NULL,
                      opened_by VARCHAR(255) NULL,
                      ending TEXT NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS door_attempt (
                      door_date DATE NOT NULL,
                      user_id VARCHAR(255) NOT NULL,
                      attempt_number BIGINT NULL,
                      result_type VARCHAR(32) NULL,
                      attempted_at DATETIME NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (door_date, user_id),
                      UNIQUE KEY uq_door_attempt_number (door_date, attempt_number)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    /**
     * 一次性补充每日参考人数列，完成迁移后可删除本方法及启动处的调用
     *
     * @throws SQLException 表结构读取或扩表失败
     */
    public void migrateReferenceVisitorsOnce() throws SQLException {
        try (var con = connections.open()) {
            try (var ps = con.prepareStatement("SELECT * FROM daily_door WHERE 1 = 0"); var rows = ps.executeQuery()) {
                var metadata = rows.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    if ("reference_visitors".equalsIgnoreCase(metadata.getColumnName(i))) return;
                }
            }
            try (var ps = con.createStatement()) {
                ps.executeUpdate("ALTER TABLE daily_door ADD COLUMN reference_visitors DOUBLE NOT NULL DEFAULT 0");
            }
        }
    }

    /**
     * 当天门记录先加行锁，再登记访问及尝试；开门时向当天有效尝试者发放金粒，所有写入在一个事务内完成
     *
     * @param userId  官机用户 OpenID，群聊与私聊共用
     * @param attempt 是否请求消耗当天唯一一次尝试，查看及开门后的访问不消耗次数
     * @return 当前状态和昨日结果，不包含开门者标识或尚未揭晓的结局
     * @throws SQLException 数据库访问失败；事务失败时不发放尝试结果
     */
    public View visit(String userId, boolean attempt) throws SQLException {
        if (userId == null || userId.isBlank() || userId.length() > 255)
            throw new IllegalArgumentException("Invalid door user");
        for (int retry = 0; ; retry++) {
            try {
                View view = transaction(userId, attempt);
                if (view != null) return view;
                if (retry >= 3) throw new SQLException("Door date changed repeatedly");
            } catch (SQLException failure) {
                if (retry >= 3 || !("40001".equals(failure.getSQLState()) || failure.getErrorCode() == 1213
                        || failure.getErrorCode() == 1205)) throw failure;
            }
        }
    }

    private View transaction(String userId, boolean attempt) throws SQLException {
        try (var con = connections.open()) {
            con.setAutoCommit(false);
            try {
                LocalDate date = LocalDate.now(clock);
                ensureDay(con, date);
                StoredDoor stored = readDay(con, date, true);
                if (!date.equals(LocalDate.now(clock))) {
                    con.rollback();
                    return null;
                }
                Door door = stored.door();
                double reference = stored.referenceVisitors() > 0 ? stored.referenceVisitors() : initializeReference(con, date);
                boolean known = false, attempted = false;
                try (var ps = con.prepareStatement("SELECT attempt_number FROM door_attempt WHERE door_date = ? AND user_id = ? FOR UPDATE")) {
                    ps.setDate(1, Date.valueOf(date));
                    ps.setString(2, userId);
                    try (var rows = ps.executeQuery()) {
                        known = rows.next();
                        if (known) attempted = rows.getObject(1) != null;
                    }
                }
                if (!known) {
                    try (var ps = con.prepareStatement("INSERT INTO door_attempt (door_date, user_id) VALUES (?, ?)")) {
                        ps.setDate(1, Date.valueOf(date));
                        ps.setString(2, userId);
                        ps.executeUpdate();
                    }
                    try (var ps = con.prepareStatement("UPDATE daily_door SET visitor_count = visitor_count + 1 WHERE door_date = ?")) {
                        ps.setDate(1, Date.valueOf(date));
                        ps.executeUpdate();
                    }
                }
                Result result = Result.VIEW;
                String feedback = null;
                if (attempt) {
                    if (door.open()) result = Result.ALREADY_OPEN;
                    else if (attempted) result = Result.ALREADY_ATTEMPTED;
                    else {
                        long number = door.attempts() + 1;
                        boolean success = DailyDoorRules.opens(random, number, reference);
                        var failed = success ? null : DailyDoorRules.failure(random);
                        result = success ? Result.SUCCESS : Result.valueOf(failed.type());
                        feedback = success ? "门开了。" : failed.text();
                        LocalDateTime now = LocalDateTime.now(clock);
                        try (var ps = con.prepareStatement("UPDATE door_attempt SET attempt_number = ?, result_type = ?, attempted_at = ? WHERE door_date = ? AND user_id = ?")) {
                            ps.setLong(1, number);
                            ps.setString(2, result.name());
                            ps.setObject(3, now);
                            ps.setDate(4, Date.valueOf(date));
                            ps.setString(5, userId);
                            ps.executeUpdate();
                        }
                        try (var ps = con.prepareStatement("UPDATE daily_door SET attempt_count = ?, opened_at = ?, opened_by = ? WHERE door_date = ?")) {
                            ps.setLong(1, number);
                            ps.setObject(2, success ? now : null);
                            ps.setString(3, success ? userId : null);
                            ps.setDate(4, Date.valueOf(date));
                            ps.executeUpdate();
                        }
                        if (success) rewardParticipants(con, date);
                        door = new Door(date, door.number(), number, success ? now : null, door.ending());
                        attempted = true;
                    }
                }
                StoredDoor previous = readDay(con, date.minusDays(1), false);
                con.commit();
                if (result == Result.SUCCESS) LOG.info("每日任意门已开启，编号: {}，日期: {}，尝试人数: {}，用户: {}",
                        door.number(), date, door.attempts(), userId);
                return new View(publicDoor(door), previous == null ? null : publicDoor(previous.door()), attempted, result, feedback);
            } catch (SQLException | RuntimeException failure) {
                try {
                    con.rollback();
                } catch (SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }

    /**
     * 在开门事务内批量发放参与奖励并记录来源，只计入已有尝试编号的用户，包含本次开门者
     */
    private void rewardParticipants(Connection con, LocalDate date) throws SQLException {
        try (var ps = con.prepareStatement("""
                INSERT INTO user_loots (user_id, loots, coins)
                SELECT user_id, '[]', ? FROM door_attempt
                WHERE door_date = ? AND attempt_number IS NOT NULL ORDER BY user_id
                ON DUPLICATE KEY UPDATE coins = coins + ?
                """)) {
            ps.setInt(1, OPEN_REWARD);
            ps.setDate(2, Date.valueOf(date));
            ps.setInt(3, OPEN_REWARD);
            ps.executeUpdate();
        }
        try (var ps = con.prepareStatement("""
                INSERT INTO user_coin_gain_logs (user_id, timestamp, way, amount)
                SELECT user_id, ?, 'daily_door', ? FROM door_attempt
                WHERE door_date = ? AND attempt_number IS NOT NULL ORDER BY user_id
                """)) {
            ps.setLong(1, clock.instant().getEpochSecond());
            ps.setInt(2, OPEN_REWARD);
            ps.setDate(3, Date.valueOf(date));
            ps.executeUpdate();
        }
    }

    private static Door publicDoor(Door door) {
        return new Door(door.date(), door.number(), door.attempts(), door.openedAt(), door.open() ? door.ending() : null);
    }

    private void ensureDay(Connection con, LocalDate date) throws SQLException {
        if (readDay(con, date, false) != null) return;
        StoredDoor previous = null;
        try (var ps = con.prepareStatement("SELECT * FROM daily_door WHERE door_date < ? ORDER BY door_date DESC LIMIT 1")) {
            ps.setDate(1, Date.valueOf(date));
            try (var rows = ps.executeQuery()) {
                if (rows.next()) previous = read(rows);
            }
        }
        long number = previous == null ? 1000 : previous.door().number() + ChronoUnit.DAYS.between(previous.door().date(), date);
        try (var ps = con.prepareStatement("""
                INSERT INTO daily_door (door_date, door_number, ending) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE door_date = door_date
                """)) {
            ps.setDate(1, Date.valueOf(date));
            ps.setLong(2, number);
            ps.setString(3, DailyDoorRules.ending(random));
            ps.executeUpdate();
        }
    }

    private static double initializeReference(Connection con, LocalDate date) throws SQLException {
        double previous = DailyDoorRules.INITIAL_REFERENCE_VISITORS;
        try (var ps = con.prepareStatement("SELECT reference_visitors FROM daily_door WHERE door_date < ? AND reference_visitors > 0 ORDER BY door_date DESC LIMIT 1")) {
            ps.setDate(1, Date.valueOf(date));
            try (var rows = ps.executeQuery()) {
                if (rows.next()) previous = rows.getDouble(1);
            }
        }
        long visitors = 0;
        LocalDate oldest = date;
        try (var ps = con.prepareStatement("SELECT door_date FROM daily_door WHERE door_date < ? ORDER BY door_date LIMIT 1")) {
            ps.setDate(1, Date.valueOf(date));
            try (var rows = ps.executeQuery()) {
                if (rows.next()) {
                    oldest = rows.getDate(1).toLocalDate();
                    if (oldest.isBefore(date.minusDays(7))) oldest = date.minusDays(7);
                }
            }
        }
        try (var ps = con.prepareStatement("SELECT visitor_count FROM daily_door WHERE door_date >= ? AND door_date < ?")) {
            ps.setDate(1, Date.valueOf(date.minusDays(7)));
            ps.setDate(2, Date.valueOf(date));
            try (var rows = ps.executeQuery()) {
                while (rows.next()) {
                    visitors += rows.getLong("visitor_count");
                }
            }
        }
        double average = oldest.equals(date) ? DailyDoorRules.INITIAL_REFERENCE_VISITORS
                : visitors / (double) ChronoUnit.DAYS.between(oldest, date);
        double reference = DailyDoorRules.referenceVisitors(average, previous);
        try (var ps = con.prepareStatement("UPDATE daily_door SET reference_visitors = ? WHERE door_date = ?")) {
            ps.setDouble(1, reference);
            ps.setDate(2, Date.valueOf(date));
            ps.executeUpdate();
        }
        return reference;
    }

    private static StoredDoor readDay(Connection con, LocalDate date, boolean lock) throws SQLException {
        try (var ps = con.prepareStatement("SELECT * FROM daily_door WHERE door_date = ?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setDate(1, Date.valueOf(date));
            try (var rows = ps.executeQuery()) {
                return rows.next() ? read(rows) : null;
            }
        }
    }

    private static StoredDoor read(ResultSet rows) throws SQLException {
        return new StoredDoor(new Door(rows.getDate("door_date").toLocalDate(), rows.getLong("door_number"),
                rows.getLong("attempt_count"), rows.getObject("opened_at", LocalDateTime.class), rows.getString("ending")),
                rows.getDouble("reference_visitors"));
    }
}
