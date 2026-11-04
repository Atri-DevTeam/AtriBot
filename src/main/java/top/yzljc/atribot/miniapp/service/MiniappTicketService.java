package top.yzljc.atribot.miniapp.service;

import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.FeedbackDTO;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.function.command.FeedbackCommand;
import top.yzljc.atricore.chat.official.AsyncC2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atribot.miniapp.MiniappSessions;
import top.yzljc.atribot.utils.tools.Alert;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 当前登录用户的工单查询与操作
 *
 * @Author YZ_Ljc_
 * @ClassName MiniappTicketService
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp.service
 */
public final class MiniappTicketService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MiniappTicketService.class);
    private static final String OWNER = "f.user_id=? AND f.platform IN ('OFFICIAL_GROUP','OFFICIAL_C2C')";
    private final MiniappProfileService.Connections connections;

    public record Ticket(String id, String status, String content, String createdAt, String updatedAt) {}
    public record Entry(long id, String type, String content, String createdAt) {}
    public record Page<T>(List<T> items, boolean hasMore) {}
    public record Detail(Ticket ticket, Page<Entry> entries) {}

    public MiniappTicketService() {
        this(DatabaseManager::getConnection);
    }

    public MiniappTicketService(MiniappProfileService.Connections connections) {
        this.connections = connections;
    }

    public Page<Ticket> list(String userId, int offset) throws SQLException {
        try (var con = connections.open(); var ps = con.prepareStatement(
                "SELECT f.* FROM feedback f WHERE " + OWNER + " ORDER BY f.update_time DESC, f.id DESC LIMIT 21 OFFSET ?")) {
            ps.setString(1, userId);
            ps.setInt(2, offset);
            ps.setQueryTimeout(5);
            var items = new ArrayList<Ticket>();
            try (var rows = ps.executeQuery()) {
                while (rows.next()) items.add(ticket(rows));
            }
            return page(items, 20);
        }
    }

    public Detail detail(String userId, String id, long before) throws SQLException {
        try (var con = connections.open(); var ps = con.prepareStatement(
                "SELECT f.* FROM feedback f WHERE " + OWNER + " AND f.id=?")) {
            ps.setString(1, userId);
            ps.setString(2, id);
            ps.setQueryTimeout(5);
            Ticket ticket;
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) return null;
                ticket = ticket(rows);
            }
            try (var entries = con.prepareStatement("""
                    SELECT e.id, e.entry_type, e.content, e.create_time FROM feedback_entries e
                    JOIN feedback f ON f.id=e.feedback_id WHERE
                    """ + OWNER + """
                     AND f.id=? AND e.id<?
                    AND e.entry_type IN ('SUBMIT','APPEND','REPLY','USER_CLOSE','ADMIN_CLOSE','ADMIN_REOPEN')
                    ORDER BY e.id DESC LIMIT 51
                    """)) {
                entries.setString(1, userId);
                entries.setString(2, id);
                entries.setLong(3, before);
                entries.setQueryTimeout(5);
                var items = new ArrayList<Entry>();
                try (var rows = entries.executeQuery()) {
                    while (rows.next()) items.add(new Entry(rows.getLong("id"), rows.getString("entry_type"),
                            rows.getString("content"), rows.getString("create_time")));
                }
                return new Detail(ticket, page(items, 50));
            }
        }
    }

    public FeedbackRepository.Submission submit(MiniappSessions.Identity identity, String content, String expectedId) {
        if (content == null || content.isBlank() || content.length() > FeedbackRepository.MAX_CONTENT_LENGTH
                || content.contains("[CQ:")) throw new IllegalArgumentException("INVALID_TICKET_CONTENT");
        var feedback = new FeedbackDTO();
        feedback.setUserId(identity.userId());
        feedback.setUsername(identity.displayName() == null ? "用户" : identity.displayName());
        feedback.setPlatform("OFFICIAL_C2C");
        feedback.setSubmitContent(content.strip());
        var result = FeedbackRepository.submit(feedback, expectedId);
        if (result != null) {
            feedback.setId(result.id());
            notifyUser(identity.userId(), () -> result.appended()
                    ? FeedbackCommand.getReplyFeedbackMessage(feedback) : FeedbackCommand.getSubmitMessage(feedback));
            ThreadManager.execute(() -> Alert.notify((result.appended() ? "工单补充: " : "收到工单: ")
                    + result.id().substring(0, 8) + "\n" + feedback.getSubmitContent() + " 来自用户: "
                    + feedback.getUsername() + " (个人中心: " + identity.userId() + ")"));
        }
        return result;
    }

    public FeedbackRepository.CloseResult close(MiniappSessions.Identity identity, String id) {
        var result = FeedbackRepository.close(id, identity.userId(), "OFFICIAL_C2C", identity.userId(), identity.displayName());
        if (result == FeedbackRepository.CloseResult.CLOSED) {
            var ticket = new FeedbackDTO();
            ticket.setId(id);
            notifyUser(identity.userId(), () -> FeedbackCommand.getClosedMessage(ticket));
        }
        return result;
    }

    private static void notifyUser(String userId, Supplier<Markdown> message) {
        ThreadManager.execute(() -> {
            if (!OfficialUsers.isFriend(userId) || !OfficialUsers.isC2CPushEnabled(userId)) return;
            try {
                AsyncC2CChat.sendMessage(userId, message.get()).whenComplete((id, error) -> {
                    if (error != null || id == null) LOG.warn("个人中心工单操作通知未送达，用户: {}", userId, error);
                });
            } catch (RuntimeException e) {
                LOG.warn("个人中心工单操作通知发送失败，用户: {}", userId, e);
            }
        });
    }

    private static Ticket ticket(ResultSet rows) throws SQLException {
        return new Ticket(rows.getString("id"), rows.getString("status"), rows.getString("submit_content"),
                rows.getString("create_time"), rows.getString("update_time"));
    }

    private static <T> Page<T> page(List<T> items, int limit) {
        return new Page<>(List.copyOf(items.subList(0, Math.min(items.size(), limit))), items.size() > limit);
    }
}
