package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupBindingRepository
 * @Created_at 2026/09/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public class GroupBindingRepository {
    public static final String SETTING = "bound_groups";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Connections connections;
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    public record Binding(String groupId, String groupNumber, String name, String boundAt) {}
    public record WelcomeData(ObjectNode config, Boolean enabled) {}

    public record Group(String groupId, String groupNumber, String name, String description, String category,
                        List<String> tags, Integer memberCount, String joinedAt, String botRole,
                        String receiveMode, boolean proactive, boolean restricted, String boundAt, boolean available) {}

    public GroupBindingRepository() { this(DatabaseManager::getConnection); }
    public GroupBindingRepository(Connections connections) { this.connections = connections; }

    public String resolve(String input) throws SQLException {
        try (var con = connections.open(); var ps = con.prepareStatement(
                "SELECT group_openId FROM official_groups WHERE group_openId = ? OR CAST(real_group_id AS CHAR) = ? LIMIT 2")) {
            ps.setString(1, input); ps.setString(2, input);
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) return null;
                String id = rows.getString(1);
                return rows.next() ? null : id;
            }
        }
    }

    public List<Binding> list(String userId) throws SQLException {
        try (var con = connections.open()) {
            ObjectNode bindings = bindings(readSettings(con, userId, false));
            List<Binding> result = new ArrayList<>();
            var entries = bindings.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                result.add(new Binding(entry.getKey(), entry.getValue().path("group_number").asText(null),
                        entry.getValue().path("group_name").asText(null), entry.getValue().path("bound_at").asText(null)));
            }
            return List.copyOf(result);
        }
    }

    /** Fetch details for one owned binding, only when its detail page is opened. */
    public Group detail(String userId, String groupId) throws SQLException {
        try (var con = connections.open()) {
            JsonNode binding = bindings(readSettings(con, userId, false)).get(groupId);
            if (binding == null) return null;
            String name = binding.path("group_name").asText(null), number = binding.path("group_number").asText(null);
            String boundAt = binding.path("bound_at").asText(null);
            try (var ps = con.prepareStatement("SELECT * FROM official_groups WHERE group_openId = ?")) {
                ps.setString(1, groupId);
                try (var rows = ps.executeQuery()) {
                    if (!rows.next()) return new Group(groupId, number, name, null, null, List.of(), null,
                            null, null, null, false, false, boundAt, false);
                    List<String> tags = new ArrayList<>();
                    String rawTags = rows.getString("group_tags");
                    if (rawTags != null) {
                        try { var parsed = JSON.readTree(rawTags); if (parsed.isArray()) parsed.forEach(tag -> { if (tag.isTextual()) tags.add(tag.asText()); }); }
                        catch (Exception ignored) { /* Other group fields remain usable. */ }
                    }
                    int count = rows.getInt("group_member_num");
                    Integer memberCount = rows.wasNull() ? null : count;
                    return new Group(groupId, number, name, rows.getString("group_finger_memo"),
                            rows.getString("group_class_text"), List.copyOf(tags), memberCount, rows.getString("joined_at"),
                            rows.getString("member_role"), rows.getString("recv_msg_setting"), rows.getBoolean("allow_proactive_msg"),
                            rows.getBoolean("is_blacklisted"), boundAt, true);
                }
            }
        }
    }

    /** Read the welcome configuration only after checking the authenticated user's binding. */
    public WelcomeData welcome(String userId, String groupId) throws SQLException {
        try (var con = connections.open()) {
            if (!bindings(readSettings(con, userId, false)).has(groupId)) return null;
            ObjectNode config = null;
            try (var ps = con.prepareStatement("SELECT config FROM group_join_welcome WHERE group_openId = ?")) {
                ps.setString(1, groupId);
                try (var rows = ps.executeQuery()) {
                    if (rows.next()) {
                        String raw = rows.getString(1);
                        if (raw != null && !raw.isBlank()) config = parseSettings(raw);
                    }
                }
            }
            Boolean enabled = null;
            try (var ps = con.prepareStatement("SELECT functions FROM group_function_list WHERE group_openId = ?")) {
                ps.setString(1, groupId);
                try (var rows = ps.executeQuery()) {
                    if (rows.next()) {
                        var flag = parseSettings(rows.getString(1)).path("member_add_welcome").path("enabled");
                        if (flag.isBoolean()) enabled = flag.asBoolean();
                    }
                }
            }
            return new WelcomeData(config, enabled);
        }
    }

    public void unbind(String userId, String groupId) throws SQLException {
        try (var con = connections.open()) {
            con.setAutoCommit(false);
            try {
                ObjectNode settings = readSettings(con, userId, true);
                ObjectNode owned = bindings(settings);
                if (owned.remove(groupId) != null) {
                    settings.set(SETTING, owned);
                    try (var ps = con.prepareStatement("UPDATE official_users SET user_settings = ? WHERE user_openId = ?")) {
                        ps.setString(1, settings.toString()); ps.setString(2, userId); ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (Exception e) {
                try { con.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("Cannot remove group binding", e);
            } finally { con.setAutoCommit(true); }
        }
    }

    private static ObjectNode readSettings(Connection con, String userId, boolean lock) throws SQLException {
        try (var ps = con.prepareStatement("SELECT user_settings FROM official_users WHERE user_openId = ?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setString(1, userId);
            try (var rows = ps.executeQuery()) { return parseSettings(rows.next() ? rows.getString(1) : null); }
        }
    }

    /** Only called after a verified official-group OWNER message. Row locks preserve unrelated settings. */
    public void bind(String userId, String groupId, String boundAt) throws SQLException {
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,256}")) throw new SQLException("Invalid group ID");
        String path = "$." + SETTING + ".\"" + groupId + "\"";
        try (var con = connections.open()) {
            con.setAutoCommit(false);
            try {
                // The group row serializes competing owner verifications, including across processes.
                String groupName, groupNumber;
                try (var ps = con.prepareStatement("SELECT group_name, real_group_id FROM official_groups WHERE group_openId = ? FOR UPDATE")) {
                    ps.setString(1, groupId);
                    try (var rows = ps.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Group no longer available");
                        groupName = rows.getString("group_name"); groupNumber = rows.getString("real_group_id");
                    }
                }
                try (var ps = con.prepareStatement("INSERT INTO official_users (user_openId, role, permissions) VALUES (?, 'USER', '') ON DUPLICATE KEY UPDATE user_openId = user_openId")) {
                    ps.setString(1, userId); ps.executeUpdate();
                }
                Map<String, ObjectNode> users = new LinkedHashMap<>();
                try (var ps = con.prepareStatement("SELECT user_openId, user_settings FROM official_users WHERE user_openId = ? OR JSON_CONTAINS_PATH(user_settings, 'one', ?) = 1 ORDER BY user_openId FOR UPDATE")) {
                    ps.setString(1, userId); ps.setString(2, path);
                    try (var rows = ps.executeQuery()) {
                        while (rows.next()) users.put(rows.getString("user_openId"), parseSettings(rows.getString("user_settings")));
                    }
                }
                if (!users.containsKey(userId)) throw new SQLException("User settings unavailable");
                for (var entry : users.entrySet()) {
                    ObjectNode owned = bindings(entry.getValue());
                    if (entry.getKey().equals(userId)) {
                        if (!owned.has(groupId) && owned.size() >= 100) throw new SQLException("Group binding limit reached");
                        ObjectNode binding = owned.path(groupId) instanceof ObjectNode old ? old : JSON.createObjectNode();
                        binding.put("group_name", groupName);
                        binding.put("group_number", groupNumber);
                        if (!binding.hasNonNull("bound_at")) binding.put("bound_at", boundAt);
                        owned.set(groupId, binding);
                    } else owned.remove(groupId); // A newly verified owner replaces a previous owner's binding.
                    entry.getValue().set(SETTING, owned);
                    try (var ps = con.prepareStatement("UPDATE official_users SET user_settings = ? WHERE user_openId = ?")) {
                        ps.setString(1, entry.getValue().toString()); ps.setString(2, entry.getKey()); ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (Exception e) {
                try { con.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("Cannot save group binding", e);
            } finally { con.setAutoCommit(true); }
        }
    }

    private static ObjectNode parseSettings(String raw) throws SQLException {
        try {
            JsonNode value = raw == null || raw.isBlank() ? JSON.createObjectNode() : JSON.readTree(raw);
            if (value instanceof ObjectNode object) return object;
        } catch (Exception e) { throw new SQLException("Invalid user settings", e); }
        throw new SQLException("Invalid user settings");
    }

    private static ObjectNode bindings(ObjectNode settings) throws SQLException {
        JsonNode value = settings.get(SETTING);
        if (value == null || value.isNull()) return JSON.createObjectNode();
        if (value instanceof ObjectNode object && object.size() <= 100) return object;
        throw new SQLException("Invalid group bindings");
    }
}
