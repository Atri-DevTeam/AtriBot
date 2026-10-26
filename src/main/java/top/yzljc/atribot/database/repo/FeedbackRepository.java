package top.yzljc.atribot.database.repo;

import com.google.common.util.concurrent.Striped;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.FeedbackDTO;
import top.yzljc.atribot.database.FeedbackEntry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * 工单状态、会话记录及待送达回复的持久化
 *
 * @Author YZ_Ljc_
 * @ClassName FeedbackRepository
 * @Created_at 2026/06/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class FeedbackRepository {
    public static final int MAX_CONTENT_LENGTH = 4000;
    private static final Striped<Lock> SUBMISSION_LOCKS = Striped.lock(128);
    private static final Striped<Lock> DELIVERY_LOCKS = Striped.lock(128);

    public record Submission(String id, boolean appended) {}
    public record Reply(FeedbackDTO ticket, FeedbackEntry entry) {}
    public enum CloseResult {CLOSED, ALREADY_CLOSED, NOT_FOUND, FAILED}
    public enum ReopenResult {REOPENED, ALREADY_OPEN, OTHER_OPEN, NOT_FOUND, FAILED}

    public static void init() {
        try (var con = DatabaseManager.getConnection()) {
            init(con);
            log.info("工单数据库表初始化完成");
        } catch (SQLException e) {
            throw new IllegalStateException("初始化工单数据库表失败", e);
        }
    }

    static void init(Connection con) throws SQLException {
        try (var st = con.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS feedback (
                      id VARCHAR(36) PRIMARY KEY, platform VARCHAR(32) NOT NULL,
                      user_id VARCHAR(255) NOT NULL, username VARCHAR(255) NOT NULL,
                      group_id VARCHAR(255), submit_content TEXT NOT NULL,
                      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      is_read BOOLEAN NOT NULL DEFAULT FALSE, reply_content TEXT,
                      reply_time DATETIME,
                      status VARCHAR(16), update_time DATETIME, closed_time DATETIME,
                      INDEX idx_user_id (user_id), INDEX idx_create_time (create_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            var columns = new HashSet<String>();
            try (var rs = st.executeQuery("SELECT * FROM feedback WHERE 1=0")) {
                var meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
            }
            if (!columns.contains("status")) st.executeUpdate("ALTER TABLE feedback ADD COLUMN status VARCHAR(16)");
            if (!columns.contains("update_time")) st.executeUpdate("ALTER TABLE feedback ADD COLUMN update_time DATETIME");
            if (!columns.contains("closed_time")) st.executeUpdate("ALTER TABLE feedback ADD COLUMN closed_time DATETIME");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS feedback_entries (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY, feedback_id VARCHAR(36) NOT NULL,
                      entry_type VARCHAR(16) NOT NULL, content TEXT NOT NULL,
                      actor_id VARCHAR(255), actor_name VARCHAR(255), actor_avatar_url TEXT, create_time DATETIME NOT NULL,
                      platform VARCHAR(32) NOT NULL, group_id VARCHAR(255),
                      source_message_id TEXT, source_message_time DATETIME(3),
                      silent BOOLEAN NOT NULL DEFAULT FALSE,
                      delivered BOOLEAN NOT NULL DEFAULT TRUE, legacy_key VARCHAR(64) UNIQUE,
                      INDEX idx_feedback_entry (feedback_id, id), INDEX idx_delivery (delivered, id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            columns.clear();
            try (var rs = st.executeQuery("SELECT * FROM feedback_entries WHERE 1=0")) {
                var meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
            }
            if (!columns.contains("source_message_id")) st.executeUpdate("ALTER TABLE feedback_entries ADD COLUMN source_message_id TEXT");
            if (!columns.contains("source_message_time")) st.executeUpdate("ALTER TABLE feedback_entries ADD COLUMN source_message_time DATETIME(3)");
            if (!columns.contains("actor_avatar_url")) st.executeUpdate("ALTER TABLE feedback_entries ADD COLUMN actor_avatar_url TEXT");
        }
        transaction(con, () -> {
            try (var st = con.createStatement()) {
                st.executeUpdate("""
                        INSERT INTO feedback_entries (feedback_id, entry_type, content, actor_id, actor_name,
                          create_time, platform, group_id, legacy_key)
                        SELECT f.id, 'SUBMIT', f.submit_content, f.user_id, f.username, f.create_time,
                          f.platform, f.group_id, CONCAT('submit:', f.id) FROM feedback f
                        WHERE f.status IS NULL AND NOT EXISTS (
                          SELECT 1 FROM feedback_entries e WHERE e.legacy_key = CONCAT('submit:', f.id))
                        """);
                st.executeUpdate("""
                        INSERT INTO feedback_entries (feedback_id, entry_type, content, create_time,
                          platform, group_id, delivered, legacy_key)
                        SELECT f.id, 'REPLY', f.reply_content, COALESCE(f.reply_time, f.create_time),
                          f.platform, f.group_id, f.is_read, CONCAT('reply:', f.id) FROM feedback f
                        WHERE f.status IS NULL AND f.reply_content IS NOT NULL AND NOT EXISTS (
                          SELECT 1 FROM feedback_entries e WHERE e.legacy_key = CONCAT('reply:', f.id))
                        """);
                st.executeUpdate("""
                        UPDATE feedback SET status = CASE WHEN reply_content IS NULL THEN 'PENDING' ELSE 'PROCESSING' END,
                          update_time = COALESCE(reply_time, create_time) WHERE status IS NULL
                        """);
            }
            return null;
        });
    }

    /**
     * 同一平台身份的未关闭工单优先追加，群聊与私聊共用 QQ 身份
     *
     * @param feedback 本次提交内容及来源
     * @return 工单编号和是否追加，保存失败返回 null
     */
    public static Submission submit(FeedbackDTO feedback) {
        try (var con = DatabaseManager.getConnection()) {
            return submit(con, feedback);
        } catch (SQLException e) {
            log.error("保存工单失败，用户: {}", feedback.getUserId(), e);
            return null;
        }
    }

    static Submission submit(Connection con, FeedbackDTO feedback) throws SQLException {
        validateContent(feedback.getSubmitContent());
        String family = platformFamily(feedback.getPlatform());
        var lock = SUBMISSION_LOCKS.get(family + ":" + feedback.getUserId());
        lock.lock();
        int isolation = con.getTransactionIsolation();
        try {
            con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            for (int attempt = 0; ; attempt++) {
                try {
                    return transaction(con, () -> {
                        FeedbackDTO current = null;
                        try (var ps = con.prepareStatement("SELECT * FROM feedback WHERE user_id = ? AND "
                                + platformCondition(feedback.getPlatform())
                                + " AND status <> 'CLOSED' ORDER BY create_time DESC, id DESC LIMIT 1 FOR UPDATE")) {
                            ps.setString(1, feedback.getUserId());
                            try (var rs = ps.executeQuery()) { if (rs.next()) current = rowToDTO(rs); }
                        }
                        Timestamp now = new Timestamp(System.currentTimeMillis());
                        boolean appended = current != null;
                        String id = appended ? current.getId() : UUID.randomUUID().toString();
                        if (appended) {
                            try (var ps = con.prepareStatement("UPDATE feedback SET platform=?, group_id=?, username=?, update_time=?, status='PENDING' WHERE id=?")) {
                                ps.setString(1, feedback.getPlatform()); ps.setString(2, feedback.getGroupId());
                                ps.setString(3, feedback.getUsername()); ps.setTimestamp(4, now); ps.setString(5, id);
                                ps.executeUpdate();
                            }
                        } else {
                            try (var ps = con.prepareStatement("""
                                    INSERT INTO feedback (id, platform, user_id, username, group_id, submit_content,
                                      create_time, update_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                                    """)) {
                                ps.setString(1, id); ps.setString(2, feedback.getPlatform()); ps.setString(3, feedback.getUserId());
                                ps.setString(4, feedback.getUsername()); ps.setString(5, feedback.getGroupId());
                                ps.setString(6, feedback.getSubmitContent()); ps.setTimestamp(7, now); ps.setTimestamp(8, now);
                                ps.executeUpdate();
                            }
                        }
                        addEntry(con, id, appended ? "APPEND" : "SUBMIT", feedback.getSubmitContent(), feedback.getUserId(),
                                feedback.getUsername(), now, feedback.getPlatform(), feedback.getGroupId(), false,
                                feedback.getSourceMessageId(), feedback.getSourceMessageTime(), null);
                        return new Submission(id, appended);
                    });
                } catch (SQLException e) {
                    if (attempt >= 2 || !"40001".equals(e.getSQLState())) throw e;
                }
            }
        } finally {
            try { con.setTransactionIsolation(isolation); } finally { lock.unlock(); }
        }
    }

    public static Reply reply(String id, String content, boolean silent, String actorId, String actorName) {
        return reply(id, content, silent, actorId, actorName, false, null);
    }

    public static Reply reply(String id, String content, boolean silent, String actorId, String actorName,
                              boolean privateReply, String actorAvatarUrl) {
        return reply(id, content, silent, actorId, actorName, privateReply, actorAvatarUrl, false);
    }

    public static Reply reply(String id, String content, boolean silent, String actorId, String actorName,
                              boolean privateReply, String actorAvatarUrl, boolean closeTicket) {
        try (var con = DatabaseManager.getConnection()) {
            return reply(con, id, content, silent, actorId, actorName, privateReply, actorAvatarUrl, closeTicket);
        } catch (SQLException e) {
            log.error("保存工单回复失败，工单: {}", id, e);
            return null;
        }
    }

    static Reply reply(Connection con, String id, String content, boolean silent,
                       String actorId, String actorName) throws SQLException {
        return reply(con, id, content, silent, actorId, actorName, false);
    }

    static Reply reply(Connection con, String id, String content, boolean silent,
                       String actorId, String actorName, boolean privateReply) throws SQLException {
        return reply(con, id, content, silent, actorId, actorName, privateReply, null);
    }

    static Reply reply(Connection con, String id, String content, boolean silent,
                       String actorId, String actorName, boolean privateReply, String actorAvatarUrl) throws SQLException {
        return reply(con, id, content, silent, actorId, actorName, privateReply, actorAvatarUrl, false);
    }

    static Reply reply(Connection con, String id, String content, boolean silent,
                       String actorId, String actorName, boolean privateReply, String actorAvatarUrl,
                       boolean closeTicket) throws SQLException {
        validateContent(content);
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("缺少回复人身份");
        return transaction(con, () -> {
            FeedbackDTO ticket = findById(con, id, true);
            if (ticket == null || "CLOSED".equals(ticket.getStatus())) return null;
            if (privateReply && (silent || !"QQ".equals(platformFamily(ticket.getPlatform())))) return null;
            Timestamp now = new Timestamp(System.currentTimeMillis());
            String status = closeTicket ? "CLOSED" : "WAITING_USER";
            FeedbackEntry entry = addEntry(con, id, "REPLY", content, actorId, actorName, now,
                    privateReply ? "OFFICIAL_C2C" : ticket.getPlatform(), privateReply ? null : ticket.getGroupId(), silent,
                    null, null, actorAvatarUrl);
            try (var ps = con.prepareStatement("""
                    UPDATE feedback SET reply_content=?, reply_time=?, is_read=?,
                      status=?, update_time=?, closed_time=? WHERE id=?
                    """)) {
                ps.setString(1, content); ps.setTimestamp(2, now); ps.setBoolean(3, silent);
                ps.setString(4, status); ps.setTimestamp(5, now);
                ps.setTimestamp(6, closeTicket ? now : null); ps.setString(7, id); ps.executeUpdate();
            }
            if (closeTicket) {
                addEntry(con, id, "ADMIN_CLOSE", "", actorId, actorName, now,
                        ticket.getPlatform(), ticket.getGroupId(), true, null, null, actorAvatarUrl);
            }
            ticket.setReplyContent(content); ticket.setReplyTime(now);
            ticket.setStatus(status); ticket.setUpdateTime(now);
            ticket.setClosedTime(closeTicket ? now : null);
            ticket.setPlatform(entry.platform()); ticket.setGroupId(entry.groupId());
            return new Reply(ticket, entry);
        });
    }

    /**
     * 用户关闭时同时验证所属平台和用户标识，管理员关闭由管理端会话鉴权
     *
     * @param id 工单完整编号
     * @param ownerId 用户关闭时的本人标识，管理端传 null
     * @param platform 用户关闭时的平台
     * @param actorId 关闭操作者标识
     * @param actorName 关闭操作者名称快照
     * @return 关闭结果，不存在和无权访问均返回 NOT_FOUND
     */
    public static CloseResult close(String id, String ownerId, String platform, String actorId, String actorName) {
        return close(id, ownerId, platform, actorId, actorName, null);
    }

    public static CloseResult close(String id, String ownerId, String platform, String actorId, String actorName, String actorAvatarUrl) {
        try (var con = DatabaseManager.getConnection()) {
            return close(con, id, ownerId, platform, actorId, actorName, actorAvatarUrl);
        } catch (SQLException e) {
            log.error("关闭工单失败，工单: {}", id, e);
            return CloseResult.FAILED;
        }
    }

    static CloseResult close(Connection con, String id, String ownerId, String platform,
                             String actorId, String actorName) throws SQLException {
        return close(con, id, ownerId, platform, actorId, actorName, null);
    }

    static CloseResult close(Connection con, String id, String ownerId, String platform,
                             String actorId, String actorName, String actorAvatarUrl) throws SQLException {
        return transaction(con, () -> {
            FeedbackDTO ticket = findById(con, id, true);
            if (ticket == null || (ownerId != null && (!ownerId.equals(ticket.getUserId())
                    || !platformFamily(platform).equals(platformFamily(ticket.getPlatform()))))) return CloseResult.NOT_FOUND;
            if ("CLOSED".equals(ticket.getStatus())) return CloseResult.ALREADY_CLOSED;
            Timestamp now = new Timestamp(System.currentTimeMillis());
            try (var ps = con.prepareStatement("UPDATE feedback SET status='CLOSED', closed_time=?, update_time=? WHERE id=?")) {
                ps.setTimestamp(1, now); ps.setTimestamp(2, now); ps.setString(3, id); ps.executeUpdate();
            }
            addEntry(con, id, ownerId == null ? "ADMIN_CLOSE" : "USER_CLOSE", "", actorId, actorName,
                    now, ticket.getPlatform(), ticket.getGroupId(), true, null, null, actorAvatarUrl);
            return CloseResult.CLOSED;
        });
    }

    /**
     * 重新打开工单，并根据是否已有回复恢复处理状态，调用方负责管理员鉴权
     *
     * @param id 工单完整编号
     * @param actorId 管理员统一账号标识
     * @param actorName 管理员名称快照
     * @return 重新打开的结果，已打开时不重复写入记录
     */
    public static ReopenResult reopen(String id, String actorId, String actorName) {
        return reopen(id, actorId, actorName, null);
    }

    public static ReopenResult reopen(String id, String actorId, String actorName, String actorAvatarUrl) {
        try (var con = DatabaseManager.getConnection()) {
            return reopen(con, id, actorId, actorName, actorAvatarUrl);
        } catch (SQLException e) {
            log.error("重新打开工单失败，工单: {}", id, e);
            return ReopenResult.FAILED;
        }
    }

    static ReopenResult reopen(Connection con, String id, String actorId, String actorName) throws SQLException {
        return reopen(con, id, actorId, actorName, null);
    }

    static ReopenResult reopen(Connection con, String id, String actorId, String actorName, String actorAvatarUrl) throws SQLException {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("管理员标识不能为空");
        FeedbackDTO owner = findById(con, id, false);
        if (owner == null) return ReopenResult.NOT_FOUND;
        var lock = SUBMISSION_LOCKS.get(platformFamily(owner.getPlatform()) + ":" + owner.getUserId());
        lock.lock();
        int isolation = con.getTransactionIsolation();
        try {
            con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            return reopenLocked(con, id, actorId, actorName, actorAvatarUrl);
        } finally {
            try { con.setTransactionIsolation(isolation); } finally { lock.unlock(); }
        }
    }

    private static ReopenResult reopenLocked(Connection con, String id, String actorId, String actorName, String actorAvatarUrl) throws SQLException {
        return transaction(con, () -> {
            FeedbackDTO ticket = findById(con, id, true);
            if (ticket == null) return ReopenResult.NOT_FOUND;
            if (!"CLOSED".equals(ticket.getStatus())) return ReopenResult.ALREADY_OPEN;
            try (var ps = con.prepareStatement("SELECT id FROM feedback WHERE user_id=? AND "
                    + platformCondition(ticket.getPlatform()) + " AND status <> 'CLOSED' LIMIT 1 FOR UPDATE")) {
                ps.setString(1, ticket.getUserId());
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) return ReopenResult.OTHER_OPEN;
                }
            }
            Timestamp now = new Timestamp(System.currentTimeMillis());
            String status = ticket.getReplyContent() == null ? "PENDING" : "WAITING_USER";
            try (var ps = con.prepareStatement("UPDATE feedback SET status=?, closed_time=NULL, update_time=? WHERE id=?")) {
                ps.setString(1, status); ps.setTimestamp(2, now); ps.setString(3, id); ps.executeUpdate();
            }
            addEntry(con, id, "ADMIN_REOPEN", "", actorId, actorName,
                    now, ticket.getPlatform(), ticket.getGroupId(), true, null, null, actorAvatarUrl);
            return ReopenResult.REOPENED;
        });
    }

    public static List<FeedbackDTO> findPaginated(String filter, int page, int pageSize) {
        String sql = """
                SELECT f.*,
                  (SELECT e.content FROM feedback_entries e WHERE e.feedback_id=f.id AND e.entry_type='APPEND' ORDER BY e.id DESC LIMIT 1) AS latest_content,
                  (SELECT e.create_time FROM feedback_entries e WHERE e.feedback_id=f.id AND e.entry_type='APPEND' ORDER BY e.id DESC LIMIT 1) AS latest_content_time,
                  (SELECT e.actor_name FROM feedback_entries e WHERE e.feedback_id=f.id AND e.entry_type='REPLY' ORDER BY e.id DESC LIMIT 1) AS reply_actor_name
                FROM feedback f
                """ + filterCondition(filter) + " ORDER BY update_time DESC, id DESC LIMIT ? OFFSET ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setInt(1, pageSize); ps.setLong(2, (long) (page - 1) * pageSize);
            List<FeedbackDTO> result = new ArrayList<>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    var ticket = rowToDTO(rs);
                    ticket.setLatestContent(rs.getString("latest_content"));
                    ticket.setLatestContentTime(rs.getTimestamp("latest_content_time"));
                    ticket.setReplyActorName(rs.getString("reply_actor_name"));
                    result.add(ticket);
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("读取工单列表失败", e);
        }
    }

    public static List<FeedbackEntry> entries(String id, long before) {
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement("""
                SELECT * FROM feedback_entries WHERE feedback_id=? AND id < ? ORDER BY id DESC LIMIT 50
                """)) {
            ps.setString(1, id); ps.setLong(2, before > 0 ? before : Long.MAX_VALUE);
            List<FeedbackEntry> entries = new ArrayList<>();
            try (var rs = ps.executeQuery()) { while (rs.next()) entries.add(rowToEntry(rs)); }
            return entries;
        } catch (SQLException e) { throw new IllegalStateException("读取工单记录失败", e); }
    }

    /**
     * 同一工单的队列投递与回复删除共用互斥锁
     *
     * @param id 工单完整编号
     * @return 工单投递锁，调用方负责释放
     */
    public static Lock deliveryLock(String id) {
        return DELIVERY_LOCKS.get(id);
    }

    public static Set<Long> deletableReplyIds(String id) {
        try (var con = DatabaseManager.getConnection()) {
            return deletableReplyIds(con, id);
        } catch (SQLException e) { throw new IllegalStateException("读取回复投递状态失败", e); }
    }

    static Set<Long> deletableReplyIds(Connection con, String id) throws SQLException {
        try (var ps = con.prepareStatement("""
                SELECT e.id FROM feedback_entries e WHERE e.feedback_id=? AND e.entry_type='REPLY' AND e.silent=FALSE
                AND (e.delivered=FALSE OR EXISTS (
                  SELECT 1 FROM pending_notice n WHERE n.source='feedback' AND n.is_delivered=FALSE
                    AND (n.source_id=CONCAT(e.feedback_id, ':', e.id)
                      OR (e.legacy_key=CONCAT('reply:', e.feedback_id) AND n.source_id=e.feedback_id))))
                AND NOT EXISTS (
                  SELECT 1 FROM pending_notice n WHERE n.source='feedback' AND n.is_delivered=TRUE
                    AND (n.source_id=CONCAT(e.feedback_id, ':', e.id)
                      OR (e.legacy_key=CONCAT('reply:', e.feedback_id) AND n.source_id=e.feedback_id)))
                """)) {
            ps.setString(1, id);
            Set<Long> result = new HashSet<>();
            try (var rs = ps.executeQuery()) { while (rs.next()) result.add(rs.getLong(1)); }
            return result;
        }
    }

    public static boolean deletePendingReply(String id, long entryId, String actorId, String actorName, String avatarUrl) {
        Lock lock = deliveryLock(id);
        if (!lock.tryLock()) return false;
        try (var con = DatabaseManager.getConnection()) {
            return deletePendingReply(con, id, entryId, actorId, actorName, avatarUrl);
        } catch (SQLException e) {
            throw new IllegalStateException("删除待送达回复失败", e);
        } finally { lock.unlock(); }
    }

    static boolean deletePendingReply(Connection con, String id, long entryId, String actorId,
                                      String actorName, String avatarUrl) throws SQLException {
        return transaction(con, () -> {
            FeedbackDTO ticket = findById(con, id, true);
            if (ticket == null) return false;
            boolean legacy;
            try (var ps = con.prepareStatement("SELECT legacy_key FROM feedback_entries WHERE id=? AND feedback_id=? FOR UPDATE")) {
                ps.setLong(1, entryId); ps.setString(2, id);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    legacy = ("reply:" + id).equals(rs.getString(1));
                }
            }
            if (!deletableReplyIds(con, id).contains(entryId)) return false;
            try (var ps = con.prepareStatement("DELETE FROM pending_notice WHERE source='feedback' AND is_delivered=FALSE AND (source_id=? OR source_id=?)")) {
                ps.setString(1, id + ":" + entryId); ps.setString(2, legacy ? id : id + ":" + entryId);
                ps.executeUpdate();
            }
            try (var ps = con.prepareStatement("UPDATE feedback_entries SET entry_type='REPLY_DELETED', content='', delivered=TRUE WHERE id=?")) {
                ps.setLong(1, entryId); ps.executeUpdate();
            }
            Timestamp now = new Timestamp(System.currentTimeMillis());
            addEntry(con, id, "ADMIN_DELETE", "删除待送达回复 #" + entryId, actorId, actorName, now,
                    ticket.getPlatform(), ticket.getGroupId(), true, null, null, avatarUrl);
            String content = null;
            Timestamp replyTime = null;
            boolean read = false;
            try (var ps = con.prepareStatement("SELECT content, create_time, delivered FROM feedback_entries WHERE feedback_id=? AND entry_type='REPLY' ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, id);
                try (var rs = ps.executeQuery()) {
                    if (rs.next()) { content = rs.getString(1); replyTime = rs.getTimestamp(2); read = rs.getBoolean(3); }
                }
            }
            String status = ticket.getStatus();
            if (!"CLOSED".equals(status)) {
                status = "PENDING";
                try (var ps = con.prepareStatement("SELECT entry_type FROM feedback_entries WHERE feedback_id=? AND entry_type IN ('REPLY','SUBMIT','APPEND') ORDER BY id DESC LIMIT 1")) {
                    ps.setString(1, id);
                    try (var rs = ps.executeQuery()) { if (rs.next() && "REPLY".equals(rs.getString(1))) status = "WAITING_USER"; }
                }
            }
            try (var ps = con.prepareStatement("UPDATE feedback SET reply_content=?, reply_time=?, is_read=?, status=?, update_time=? WHERE id=?")) {
                ps.setString(1, content); ps.setTimestamp(2, replyTime); ps.setBoolean(3, read);
                ps.setString(4, status); ps.setTimestamp(5, now); ps.setString(6, id); ps.executeUpdate();
            }
            return true;
        });
    }

    public static int count(String filter) {
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement("SELECT COUNT(*) FROM feedback" + filterCondition(filter));
             var rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { throw new IllegalStateException("统计工单失败", e); }
    }

    public static int countUnreplied() { return count("pending"); }
    public static List<FeedbackDTO> findUnrepliedPaginated(int page, int pageSize) { return findPaginated("pending", page, pageSize); }

    private static String filterCondition(String filter) {
        return switch (filter == null ? "pending" : filter) {
            case "all" -> "";
            case "processing" -> " WHERE status='PROCESSING'";
            case "waiting" -> " WHERE status='WAITING_USER'";
            case "closed" -> " WHERE status='CLOSED'";
            default -> " WHERE status='PENDING'";
        };
    }

    public static FeedbackDTO findById(String id) {
        try (var con = DatabaseManager.getConnection()) { return findById(con, id, false); }
        catch (SQLException e) { throw new IllegalStateException("读取工单失败", e); }
    }

    static FeedbackDTO findById(Connection con, String id, boolean lock) throws SQLException {
        try (var ps = con.prepareStatement("SELECT * FROM feedback WHERE id=?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) { return rs.next() ? rowToDTO(rs) : null; }
        }
    }

    public static FeedbackDTO findByIdPrefix(String prefix) { return findByIdPrefix(prefix, null, null); }

    public static FeedbackDTO findByIdPrefix(String prefix, String ownerId, String platform) {
        if (prefix == null || !prefix.matches("[a-fA-F0-9-]{8,36}")) return null;
        String sql = "SELECT * FROM feedback WHERE id LIKE ?" + (ownerId == null ? "" : " AND user_id=? AND " + platformCondition(platform)) + " LIMIT 2";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            if (ownerId != null) ps.setString(2, ownerId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                var ticket = rowToDTO(rs);
                return rs.next() ? null : ticket;
            }
        } catch (SQLException e) { throw new IllegalStateException("查找工单失败", e); }
    }

    /**
     * 原子领取一条回复，主动通知与事件补发共用领取状态
     *
     * @param entryId 指定回复记录，用户被动领取时传 null
     * @param userId 被动领取的用户标识
     * @param platform 被动领取的平台，用于隔离频道和 QQ 身份
     * @return 待发送快照，无记录时返回 null
     */
    public static Reply claimReply(Long entryId, String userId, String platform) {
        try (var con = DatabaseManager.getConnection()) {
            return claimReply(con, entryId, userId, platform);
        } catch (SQLException e) {
            log.error("领取工单回复失败，记录: {}", entryId, e);
            return null;
        }
    }

    static Reply claimReply(Connection con, Long entryId, String userId, String platform) throws SQLException {
            return transaction(con, () -> {
                String sql = "SELECT e.* FROM feedback_entries e JOIN feedback f ON f.id=e.feedback_id "
                        + "WHERE e.entry_type='REPLY' AND e.delivered=FALSE AND e.silent=FALSE "
                        + (entryId != null ? "AND e.id=?" : "AND f.user_id=? AND " + platformCondition(platform).replace("platform", "e.platform")
                            + ("OFFICIAL_C2C".equals(platform) ? "" : " AND e.platform <> 'OFFICIAL_C2C'"))
                        + " ORDER BY e.id LIMIT 1 FOR UPDATE";
                FeedbackEntry entry;
                try (var ps = con.prepareStatement(sql)) {
                    if (entryId != null) ps.setLong(1, entryId); else ps.setString(1, userId);
                    try (var rs = ps.executeQuery()) { if (!rs.next()) return null; entry = rowToEntry(rs); }
                }
                try (var ps = con.prepareStatement("UPDATE feedback_entries SET delivered=TRUE WHERE id=?")) {
                    ps.setLong(1, entry.id()); ps.executeUpdate();
                }
                FeedbackDTO ticket = findById(con, entry.feedbackId(), false);
                ticket.setPlatform(entry.platform()); ticket.setGroupId(entry.groupId());
                ticket.setReplyContent(entry.content()); ticket.setReplyTime(entry.createTime());
                try (var ps = con.prepareStatement("""
                        SELECT platform, group_id, source_message_id, source_message_time FROM feedback_entries
                        WHERE feedback_id=? AND id<? AND entry_type IN ('SUBMIT','APPEND') ORDER BY id DESC LIMIT 1
                        """)) {
                    ps.setString(1, entry.feedbackId()); ps.setLong(2, entry.id());
                    try (var rs = ps.executeQuery()) {
                        if (rs.next() && Objects.equals(entry.platform(), rs.getString("platform"))
                                && Objects.equals(entry.groupId(), rs.getString("group_id"))) {
                            ticket.setSourceMessageId(rs.getString("source_message_id"));
                            ticket.setSourceMessageTime(rs.getTimestamp("source_message_time"));
                        }
                    }
                }
                return new Reply(ticket, entry);
            });
    }

    public static void releaseReply(long entryId) {
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement("UPDATE feedback_entries SET delivered=FALSE WHERE id=? AND silent=FALSE")) {
            ps.setLong(1, entryId); ps.executeUpdate();
        } catch (SQLException e) { log.error("恢复工单待发送状态失败，记录: {}", entryId, e); }
    }

    /**
     * 读取队列通知对应的原始回复，避免多次回复后误用最新一条内容
     *
     * @param sourceId 通知来源编号，旧记录为工单编号，新记录为工单编号与回复编号的组合
     * @return 对应回复快照，不存在或属于静默回复时返回 null
     */
    public static FeedbackDTO queuedReply(String sourceId) {
        if (sourceId == null) return null;
        try (var con = DatabaseManager.getConnection()) {
            return queuedReply(con, sourceId);
        } catch (SQLException e) { throw new IllegalStateException("读取待补发工单回复失败", e); }
    }

    static FeedbackDTO queuedReply(Connection con, String sourceId) throws SQLException {
        String[] parts = sourceId.split(":", -1);
        if (parts.length > 2) return null;
        long entryId = 0;
        if (parts.length == 2) {
            try { entryId = Long.parseLong(parts[1]); } catch (NumberFormatException e) { return null; }
            if (entryId <= 0) return null;
        }
        var ticket = findById(con, parts[0], false);
        if (ticket == null) return null;
        String sql = "SELECT * FROM feedback_entries WHERE feedback_id=? AND entry_type='REPLY' AND silent=FALSE AND "
                + (parts.length == 2 ? "id=?" : "legacy_key=?");
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, parts[0]);
            if (parts.length == 2) ps.setLong(2, entryId); else ps.setString(2, "reply:" + parts[0]);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ticket.setReplyContent(rs.getString("content"));
                ticket.setReplyTime(rs.getTimestamp("create_time"));
                return ticket;
            }
        }
    }

    private static FeedbackEntry addEntry(Connection con, String id, String type, String content, String actorId,
                                          String actorName, Timestamp time, String platform, String groupId,
                                          boolean silent, String sourceMessageId, Timestamp sourceMessageTime, String actorAvatarUrl) throws SQLException {
        try (var ps = con.prepareStatement("""
                INSERT INTO feedback_entries (feedback_id, entry_type, content, actor_id, actor_name, create_time,
                  platform, group_id, silent, delivered, source_message_id, source_message_time, actor_avatar_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, id); ps.setString(2, type); ps.setString(3, content); ps.setString(4, actorId);
            ps.setString(5, actorName); ps.setTimestamp(6, time); ps.setString(7, platform); ps.setString(8, groupId);
            ps.setBoolean(9, silent); ps.setBoolean(10, !"REPLY".equals(type) || silent);
            ps.setString(11, sourceMessageId); ps.setTimestamp(12, sourceMessageTime);
            ps.setString(13, actorAvatarUrl);
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("工单记录编号未生成");
                return new FeedbackEntry(keys.getLong(1), id, type, content, actorId, actorName, time, platform, groupId, silent, actorAvatarUrl);
            }
        }
    }

    private static String platformFamily(String platform) {
        if (platform == null) throw new IllegalArgumentException("缺少平台");
        return switch (platform) {
            case "OFFICIAL_GROUP", "OFFICIAL_C2C" -> "QQ";
            case "OFFICIAL_GUILD_CHANNEL", "OFFICIAL_GUILD_DM" -> "GUILD";
            case "NAPCAT_GROUP", "NAPCAT_PRIVATE" -> "NAPCAT";
            default -> platform;
        };
    }

    private static String platformCondition(String platform) {
        return switch (platformFamily(platform)) {
            case "QQ" -> "platform IN ('OFFICIAL_GROUP','OFFICIAL_C2C')";
            case "GUILD" -> "platform IN ('OFFICIAL_GUILD_CHANNEL','OFFICIAL_GUILD_DM')";
            case "NAPCAT" -> "platform IN ('NAPCAT_GROUP','NAPCAT_PRIVATE')";
            default -> throw new IllegalArgumentException("不支持的工单平台");
        };
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH)
            throw new IllegalArgumentException("内容长度须为 1—" + MAX_CONTENT_LENGTH + " 个字符");
    }

    private static FeedbackEntry rowToEntry(ResultSet rs) throws SQLException {
        return new FeedbackEntry(rs.getLong("id"), rs.getString("feedback_id"), rs.getString("entry_type"),
                rs.getString("content"), rs.getString("actor_id"), rs.getString("actor_name"), rs.getTimestamp("create_time"),
                rs.getString("platform"), rs.getString("group_id"), rs.getBoolean("silent"), rs.getString("actor_avatar_url"));
    }

    private static FeedbackDTO rowToDTO(ResultSet rs) throws SQLException {
        FeedbackDTO dto = new FeedbackDTO();
        dto.setId(rs.getString("id")); dto.setPlatform(rs.getString("platform")); dto.setUserId(rs.getString("user_id"));
        dto.setUsername(rs.getString("username")); dto.setGroupId(rs.getString("group_id"));
        dto.setSubmitContent(rs.getString("submit_content")); dto.setCreateTime(rs.getTimestamp("create_time"));
        dto.setRead(rs.getBoolean("is_read")); dto.setReplyContent(rs.getString("reply_content"));
        dto.setReplyTime(rs.getTimestamp("reply_time"));
        dto.setStatus(rs.getString("status")); dto.setUpdateTime(rs.getTimestamp("update_time"));
        dto.setClosedTime(rs.getTimestamp("closed_time"));
        return dto;
    }

    private static <T> T transaction(Connection con, SqlWork<T> work) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            T result = work.run();
            con.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            con.rollback();
            throw e;
        } finally { con.setAutoCommit(autoCommit); }
    }

    @FunctionalInterface
    private interface SqlWork<T> { T run() throws SQLException; }
}
