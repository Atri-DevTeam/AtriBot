package top.yzljc.atribot.function.reminder;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author YZ_Ljc_
 * @ClassName ReminderRepository
 * @Created_at 2026/09/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.reminder
 */
@Slf4j
public final class ReminderRepository implements ReminderStore {
    @FunctionalInterface
    public interface Connections { Connection open() throws SQLException; }

    private final Connections connections;
    private final Clock clock;
    private final AtomicLong lastCleanup = new AtomicLong();

    public ReminderRepository() {
        this(DatabaseManager::getConnection);
    }

    public ReminderRepository(Connections connections) {
        this(connections, Clock.systemUTC());
    }

    public ReminderRepository(Connections connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    @Override
    public void init() throws SQLException {
        // 全部时间保存为 epoch 毫秒，不依赖 MySQL 会话时区。
        List<String> ddl = List.of("""
                CREATE TABLE IF NOT EXISTS reminder_users (
                  user_id VARCHAR(128) NOT NULL PRIMARY KEY,
                  next_id BIGINT NOT NULL DEFAULT 1
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """, """
                CREATE TABLE IF NOT EXISTS reminder_requests (
                  user_id VARCHAR(128) NOT NULL,
                  request_id VARCHAR(128) NOT NULL,
                  created_at BIGINT NOT NULL,
                  result VARCHAR(16) NOT NULL,
                  task_id BIGINT NULL,
                  revision BIGINT NOT NULL DEFAULT 0,
                  PRIMARY KEY(user_id, request_id),
                  INDEX idx_reminder_request_window(user_id, created_at),
                  INDEX idx_reminder_request_pending(user_id, result, created_at),
                  INDEX idx_reminder_request_age(created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """, """
                CREATE TABLE IF NOT EXISTS reminder_tasks (
                  user_id VARCHAR(128) NOT NULL,
                  task_id BIGINT NOT NULL,
                  ref_idx VARCHAR(512) NOT NULL,
                  schedule_json VARCHAR(1024) NOT NULL,
                  state VARCHAR(16) NOT NULL,
                  next_run_at BIGINT NOT NULL,
                  delivery_at BIGINT NOT NULL,
                  revision BIGINT NOT NULL DEFAULT 0,
                  attempts INT NOT NULL DEFAULT 0,
                  PRIMARY KEY(user_id, task_id),
                  INDEX idx_reminder_due(state, delivery_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
                """);
        try (Connection con = connections.open()) {
            for (String sql : ddl) {
                try (PreparedStatement ps = con.prepareStatement(sql)) { ps.execute(); }
            }
        }
    }

    @Override
    public Result begin(String userId, String requestId, Long taskId, Instant now, boolean unlimited) throws SQLException {
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                lockUser(con, userId);
                try (PreparedStatement ps = prepare(con,
                        "SELECT result, task_id FROM reminder_requests WHERE user_id=? AND request_id=?", userId, requestId);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Code code = "PENDING".equals(rs.getString(1)) ? Code.BUSY : Code.valueOf(rs.getString(1));
                        con.commit();
                        return new Result(code, rs.getLong(2));
                    }
                }
                execute(con, "UPDATE reminder_requests SET result='ERROR' WHERE user_id=? AND result='PENDING' AND created_at<?",
                        userId, now.minusSeconds(120).toEpochMilli());
                Code denied = null;
                if (!unlimited && count(con, "SELECT COUNT(*) FROM reminder_requests WHERE user_id=? AND created_at>?",
                        userId, now.minus(Duration.ofMinutes(30)).toEpochMilli()) >= 30) denied = Code.LIMITED;
                else if (count(con, "SELECT COUNT(*) FROM reminder_requests WHERE user_id=? AND result='PENDING'", userId) > 0) denied = Code.BUSY;
                else if (!unlimited && taskId == null && taskCount(con, userId) >= 10) denied = Code.FULL;
                long revision = 0;
                if (denied == null && taskId != null) {
                    try (PreparedStatement ps = prepare(con,
                            "SELECT revision FROM reminder_tasks WHERE user_id=? AND task_id=? FOR UPDATE", userId, taskId);
                         ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) denied = Code.NOT_FOUND;
                        else revision = rs.getLong(1);
                    }
                }
                if (denied != null) {
                    con.commit();
                    return new Result(denied, 0);
                }
                execute(con, "INSERT INTO reminder_requests(user_id,request_id,created_at,result,task_id,revision) VALUES(?,?,?,'PENDING',?,?)",
                        userId, requestId, now.toEpochMilli(), taskId, revision);
                con.commit();
                return new Result(Code.READY, taskId == null ? 0 : taskId);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    @Override
    public Result save(String userId, String requestId, String refIdx, ReminderSchedule schedule, Instant now,
                       boolean unlimited) throws SQLException {
        Instant next = schedule.nextAfter(now, null);
        if (next == null || next.isAfter(now.plus(Duration.ofDays(366)))) {
            reject(userId, requestId, Code.AMBIGUOUS);
            return new Result(Code.AMBIGUOUS, 0);
        }
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                long nextId = lockUser(con, userId);
                Long taskId;
                long revision;
                try (PreparedStatement ps = prepare(con,
                        "SELECT result,task_id,revision FROM reminder_requests WHERE user_id=? AND request_id=? FOR UPDATE", userId, requestId);
                     ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !"PENDING".equals(rs.getString(1))) {
                        con.commit();
                        return new Result(Code.CONFLICT, 0);
                    }
                    long id = rs.getLong(2);
                    taskId = rs.wasNull() ? null : id;
                    revision = rs.getLong(3);
                }
                Code result = Code.SAVED;
                // 权限可能在 AI 解析期间被撤销；保存时再次执行普通用户的限额检查。
                if (!unlimited && count(con, "SELECT COUNT(*) FROM reminder_requests WHERE user_id=? AND created_at>?",
                        userId, now.minus(Duration.ofMinutes(30)).toEpochMilli()) > 30) {
                    result = Code.LIMITED;
                } else if (taskId == null) {
                    if (!unlimited && taskCount(con, userId) >= 10) result = Code.FULL;
                    else {
                        taskId = nextId;
                        execute(con, "UPDATE reminder_users SET next_id=next_id+1 WHERE user_id=?", userId);
                        execute(con, "INSERT INTO reminder_tasks(user_id,task_id,ref_idx,schedule_json,state,next_run_at,delivery_at) VALUES(?,?,?,?,'ACTIVE',?,?)",
                                userId, taskId, refIdx, schedule.toJson(), next.toEpochMilli(), next.toEpochMilli());
                    }
                } else {
                    int updated = execute(con,
                            "UPDATE reminder_tasks SET ref_idx=?,schedule_json=?,state='ACTIVE',next_run_at=?,delivery_at=?,revision=revision+1,attempts=0 WHERE user_id=? AND task_id=? AND revision=?",
                            refIdx, schedule.toJson(), next.toEpochMilli(), next.toEpochMilli(), userId, taskId, revision);
                    if (updated == 0) result = Code.CONFLICT;
                }
                execute(con, "UPDATE reminder_requests SET result=?,task_id=? WHERE user_id=? AND request_id=?",
                        result.name(), taskId, userId, requestId);
                con.commit();
                return new Result(result, taskId == null ? 0 : taskId);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    @Override
    public void reject(String userId, String requestId, Code code) throws SQLException {
        if (code == Code.READY || code == Code.SAVED) throw new IllegalArgumentException("Invalid rejection");
        try (Connection con = connections.open()) {
            execute(con, "UPDATE reminder_requests SET result=? WHERE user_id=? AND request_id=? AND result='PENDING'",
                    code.name(), userId, requestId);
        }
    }

    @Override
    public List<Long> list(String userId, int page) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection con = connections.open();
             PreparedStatement ps = prepare(con,
                     "SELECT task_id FROM reminder_tasks WHERE user_id=? ORDER BY task_id LIMIT 11 OFFSET ?",
                     userId, ((long) page - 1) * 10);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getLong(1));
        }
        return List.copyOf(ids);
    }

    @Override
    public boolean stop(String userId, long taskId, boolean delete) throws SQLException {
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                lockUser(con, userId);
                int updated = delete
                        ? execute(con, "DELETE FROM reminder_tasks WHERE user_id=? AND task_id=?", userId, taskId)
                        : execute(con, "UPDATE reminder_tasks SET state='DISABLED',revision=revision+1 WHERE user_id=? AND task_id=?", userId, taskId);
                con.commit();
                return updated > 0;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    @Override
    public void dispatchDue(Instant now, Delivery delivery) throws Exception {
        long previousCleanup = lastCleanup.get();
        if ((previousCleanup == 0 || now.toEpochMilli() - previousCleanup >= Duration.ofHours(1).toMillis())
                && lastCleanup.compareAndSet(previousCleanup, now.toEpochMilli())) {
            try (Connection con = connections.open()) {
                // 保留七天消息去重记录；限量清理，避免频控记录永久增长。
                execute(con, "DELETE FROM reminder_requests WHERE created_at<? AND result<>'PENDING' LIMIT 1000",
                        now.minus(Duration.ofDays(7)).toEpochMilli());
            }
        }
        List<TaskKey> due = new ArrayList<>();
        try (Connection con = connections.open();
             PreparedStatement ps = prepare(con,
                     "SELECT user_id,task_id,revision FROM reminder_tasks WHERE state='ACTIVE' AND delivery_at<=? ORDER BY delivery_at LIMIT 50", now.toEpochMilli());
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) due.add(new TaskKey(rs.getString(1), rs.getLong(2), rs.getLong(3)));
        }
        for (TaskKey key : due) {
            try {
                deliver(key, now, delivery);
            } catch (Exception e) {
                // 不记录异常消息或堆栈：第三方异常可能包含用户原文或 AI 原文。
                log.warn("提醒投递处理失败: taskId={}, errorType={}", key.id(), e.getClass().getSimpleName());
            }
        }
    }

    private void deliver(TaskKey key, Instant scanTime, Delivery delivery) throws Exception {
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = prepare(con,
                        "SELECT ref_idx,schedule_json,next_run_at,attempts FROM reminder_tasks WHERE user_id=? AND task_id=? AND revision=? AND state='ACTIVE' AND delivery_at<=? FOR UPDATE",
                        key.userId(), key.id(), key.revision(), scanTime.toEpochMilli());
                     ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { con.commit(); return; }
                    String refIdx = rs.getString(1);
                    ReminderSchedule schedule = ReminderSchedule.fromJson(ReminderAiParser.read(rs.getString(2)));
                    Instant originalDue = Instant.ofEpochMilli(rs.getLong(3));
                    int attempts = rs.getInt(4);
                    Instant now = clock.instant();
                    // 停机超出 24 小时的提醒不补发；周期任务移动到下一次，避免集中轰炸。
                    if (originalDue.isBefore(now.minus(Duration.ofHours(24)))) {
                        advance(con, key, schedule, originalDue, now);
                    } else {
                        boolean sent;
                        try {
                            sent = delivery.send(key.userId(), refIdx, ReminderService.reminderText(key.id()));
                        } catch (Exception e) {
                            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                            sent = false;
                        }
                        now = clock.instant();
                        if (sent) advance(con, key, schedule, originalDue, now);
                        else if (attempts >= 2) {
                            execute(con, "UPDATE reminder_tasks SET state='DISABLED',revision=revision+1,attempts=attempts+1 WHERE user_id=? AND task_id=?",
                                    key.userId(), key.id());
                        } else {
                            execute(con, "UPDATE reminder_tasks SET delivery_at=?,attempts=attempts+1,revision=revision+1 WHERE user_id=? AND task_id=?",
                                    now.plusSeconds(60L * (attempts + 1)).toEpochMilli(), key.userId(), key.id());
                        }
                    }
                }
                // 持有该任务行锁直到发送结束：关闭/修改成功后，旧扫描结果不能再发送。
                // 外部发送与数据库提交不构成原子事务；发送成功后进程崩溃可能导致重复投递。
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    private void advance(Connection con, TaskKey key, ReminderSchedule schedule, Instant due, Instant now) throws SQLException {
        Instant next = schedule.kind() == ReminderSchedule.Kind.ONCE ? null : schedule.nextAfter(now, due);
        if (next == null) execute(con, "DELETE FROM reminder_tasks WHERE user_id=? AND task_id=?", key.userId(), key.id());
        else execute(con, "UPDATE reminder_tasks SET next_run_at=?,delivery_at=?,attempts=0,revision=revision+1 WHERE user_id=? AND task_id=?",
                next.toEpochMilli(), next.toEpochMilli(), key.userId(), key.id());
    }

    /** 每个用户一行锁，同时约束创建配额、编号、设置请求和任务修改。 */
    private long lockUser(Connection con, String userId) throws SQLException {
        execute(con, "INSERT IGNORE INTO reminder_users(user_id) VALUES(?)", userId);
        try (PreparedStatement ps = prepare(con, "SELECT next_id FROM reminder_users WHERE user_id=? FOR UPDATE", userId);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new SQLException("Missing reminder user");
            return rs.getLong(1);
        }
    }

    private long taskCount(Connection con, String userId) throws SQLException {
        return count(con, "SELECT COUNT(*) FROM reminder_tasks WHERE user_id=?", userId);
    }

    private long count(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(con, sql, args); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private int execute(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = prepare(con, sql, args)) { return ps.executeUpdate(); }
    }

    private PreparedStatement prepare(Connection con, String sql, Object... args) throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        try {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        } catch (SQLException e) {
            ps.close();
            throw e;
        }
    }

    private record TaskKey(String userId, long id, long revision) {}
}
