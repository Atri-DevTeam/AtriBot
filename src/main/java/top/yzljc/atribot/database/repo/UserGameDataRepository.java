package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * 每个用户的游戏数据保存在 official_users.game_data；游戏对象的字段可自由扩展。
 *
 * @Author YZ_Ljc_
 * @ClassName UserGameDataRepository
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public final class UserGameDataRepository {
    private static final Logger log = LoggerFactory.getLogger(UserGameDataRepository.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UserGameDataRepository INSTANCE = new UserGameDataRepository(DatabaseManager::getConnection);

    public enum Game {minesweeper, sound, connect4, roulette, rsp}

    @FunctionalInterface
    public interface Connections {
        Connection open() throws SQLException;
    }

    private final Connections connections;

    public record Delta(String userId, long operations, long correct, long plays, long wins) {
        public Delta {
            if (userId == null || userId.isBlank() || userId.length() > 255 || operations < 0 || correct < 0
                    || plays < 0 || wins < 0 || correct > operations || wins > plays)
                throw new IllegalArgumentException("Invalid game statistics");
        }

        public static Delta operation(String userId, boolean correct) {
            return new Delta(userId, 1, correct ? 1 : 0, 0, 0);
        }

        public static Delta match(String userId, boolean won) {
            return new Delta(userId, 0, 0, 1, won ? 1 : 0);
        }
    }

    /**
     * 修改器可能因事务重试而重新执行，不应在其中发送消息或执行其他外部操作。
     */
    public record Update(String userId, Consumer<ObjectNode> change) {
    }

    public record MigrationResult(int imported, int skipped, int failed) {
    }

    public UserGameDataRepository(Connections connections) {
        this.connections = connections;
    }

    public static UserGameDataRepository instance() {
        return INSTANCE;
    }

    public static void init() {
        try (var con = DatabaseManager.getConnection(); var ps = con.createStatement()) {
            ps.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_game_stat_events (
                      game VARCHAR(32) NOT NULL,
                      event_id VARCHAR(80) COLLATE utf8mb4_bin NOT NULL,
                      user_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (game, event_id, user_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("游戏事件去重表初始化失败", e);
        }
    }

    public static void record(Game game, String eventId, List<Delta> deltas) {
        try {
            INSTANCE.append(game, eventId, deltas);
        } catch (SQLException | RuntimeException e) {
            log.error("游戏统计写入失败: game={}, event={}", game, eventId, e);
        }
    }

    public void append(Game game, String eventId, List<Delta> deltas) throws SQLException {
        if (game == null) throw new IllegalArgumentException("Invalid game");
        mutate(game.name(), eventId, deltas.stream().map(delta -> new Update(delta.userId(), node -> {
            if (game == Game.minesweeper || game == Game.sound) {
                increment(node, "operations", delta.operations());
                if (game == Game.sound) increment(node, "correct", delta.correct());
            } else {
                increment(node, "plays", delta.plays());
                increment(node, "wins", delta.wins());
            }
        })).toList());
    }

    /**
     * 任意游戏键、任意 JSON 字段；多人结算在同一事务内提交。
     */
    public int mutate(String game, String eventId, List<Update> updates) throws SQLException {
        if (game == null || !game.matches("[a-z][a-z0-9_]{0,31}") || eventId == null || eventId.isBlank() || eventId.length() > 80)
            throw new IllegalArgumentException("Invalid game event");
        if (updates.stream().anyMatch(u -> u.userId() == null || u.userId().isBlank() || u.userId().length() > 255 || u.change() == null)
                || updates.stream().map(Update::userId).distinct().count() != updates.size())
            throw new IllegalArgumentException("Invalid or duplicate participant");
        var ordered = updates.stream().sorted(Comparator.comparing(Update::userId)).toList();
        for (int attempt = 0; ; attempt++) {
            try {
                return transaction(game, eventId, ordered);
            } catch (SQLException e) {
                if (attempt == 1) throw e;
            }
        }
    }

    private int transaction(String game, String eventId, List<Update> updates) throws SQLException {
        if (updates.isEmpty()) return 0;
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                int changed = 0;
                for (Update update : updates) {
                    // 先锁用户行，跨游戏、跨实例的 JSON 读改写不会互相覆盖。
                    try (var ps = con.prepareStatement("INSERT INTO official_users (user_openId, role, permissions) VALUES (?, 'USER', '') ON DUPLICATE KEY UPDATE user_openId = user_openId")) {
                        ps.setQueryTimeout(5);
                        ps.setString(1, update.userId());
                        ps.executeUpdate();
                    }
                    ObjectNode root;
                    try (var ps = con.prepareStatement("SELECT game_data FROM official_users WHERE user_openId = ? FOR UPDATE")) {
                        ps.setQueryTimeout(5);
                        ps.setString(1, update.userId());
                        try (var rows = ps.executeQuery()) {
                            if (!rows.next()) throw new SQLException("Missing user row");
                            root = parse(rows.getString("game_data"));
                        }
                    }
                    try (var ps = con.prepareStatement("INSERT INTO user_game_stat_events (game, event_id, user_id) VALUES (?, ?, ?)")) {
                        ps.setQueryTimeout(5);
                        ps.setString(1, game);
                        ps.setString(2, eventId);
                        ps.setString(3, update.userId());
                        try {
                            ps.executeUpdate();
                        } catch (SQLException duplicate) {
                            if (duplicate.getErrorCode() == 1062 && "23000".equals(duplicate.getSQLState())) continue;
                            throw duplicate;
                        }
                    }
                    JsonNode existing = root.get(game);
                    if (existing != null && !(existing instanceof ObjectNode))
                        throw new SQLException("Game data is not an object: " + game);
                    ObjectNode node = existing == null ? root.putObject(game) : (ObjectNode) existing;
                    update.change().accept(node);
                    try (var ps = con.prepareStatement("UPDATE official_users SET game_data = ? WHERE user_openId = ?")) {
                        ps.setQueryTimeout(5);
                        ps.setString(1, root.toString());
                        ps.setString(2, update.userId());
                        ps.executeUpdate();
                    }
                    changed++;
                }
                con.commit();
                return changed;
            } catch (SQLException | RuntimeException e) {
                try {
                    con.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            }
        }
    }

    public ObjectNode read(String userId) throws SQLException {
        try (var con = connections.open(); var ps = con.prepareStatement("SELECT game_data FROM official_users WHERE user_openId = ?")) {
            ps.setQueryTimeout(5);
            ps.setString(1, userId);
            try (var rows = ps.executeQuery()) {
                return parse(rows.next() ? rows.getString("game_data") : null);
            }
        }
    }

    public static ObjectNode parse(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) return JSON.createObjectNode();
        try {
            JsonNode root = JSON.readTree(raw);
            if (root instanceof ObjectNode object) return object;
            throw new SQLException("Game data is not a JSON object");
        } catch (IOException e) {
            throw new SQLException("Invalid game JSON; refusing to overwrite", e);
        }
    }

    public int reactionRank(long bestMs) throws SQLException {
        if (bestMs <= 0) return 0;
        try (var con = connections.open(); var ps = con.prepareStatement("""
                SELECT COUNT(*) AS faster FROM official_users
                WHERE CAST(JSON_UNQUOTE(JSON_EXTRACT(game_data, '$.reaction.bestMs')) AS UNSIGNED) BETWEEN 1 AND ?
                """)) {
            ps.setQueryTimeout(5);
            ps.setLong(1, bestMs - 1);
            try (var rows = ps.executeQuery()) {
                rows.next();
                return Math.toIntExact(rows.getLong("faster") + 1);
            }
        }
    }

    public boolean recordReaction(String userId, String eventId, boolean completed, long elapsedMs, int misses, LocalDate date) throws SQLException {
        if (elapsedMs < 0 || misses < 0 || (completed && elapsedMs == 0))
            throw new IllegalArgumentException("Invalid reaction result");
        boolean[] newBest = {false};
        int changed = mutate("reaction", eventId, List.of(new Update(userId, node -> {
            newBest[0] = false;
            increment(node, "plays", 1);
            increment(node, "wins", completed ? 1 : 0);
            long best = counter(node, "bestMs");
            if (completed && (best == 0 || elapsedMs < best)) {
                node.put("bestMs", elapsedMs).put("bestMisses", misses).put("bestDate", date.toString());
                newBest[0] = true;
            }
        })));
        return changed > 0 && newBest[0];
    }

    /**
     * 一次性导入旧文件；按用户去重，可重跑补齐失败项，不修改或删除源文件。
     */
    public MigrationResult migrateLegacyReaction(Path file) throws IOException {
        if (!Files.exists(file)) return new MigrationResult(0, 0, 0);
        JsonNode root;
        try (var input = Files.newInputStream(file)) {
            root = JSON.readTree(input);
        }
        if (!(root instanceof ObjectNode)) throw new IOException("Invalid legacy reaction document");
        int imported = 0, skipped = 0, failed = 0;
        var entries = root.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            try {
                if (!(entry.getValue() instanceof ObjectNode legacy))
                    throw new IllegalArgumentException("Invalid legacy user");
                long plays = counter(legacy, "plays"), wins = counter(legacy, "wins"), best = counter(legacy, "bestMs");
                long misses = counter(legacy, "bestMisses");
                if (wins > plays || misses > Integer.MAX_VALUE)
                    throw new IllegalArgumentException("Invalid legacy counters");
                int changed = mutate("reaction", "legacy-click-train-v1", List.of(new Update(entry.getKey(), node -> {
                    long currentBest = counter(node, "bestMs");
                    increment(node, "plays", plays);
                    increment(node, "wins", wins);
                    if (best > 0 && (currentBest == 0 || best < currentBest)) {
                        node.put("bestMs", best).put("bestMisses", misses).put("bestDate", legacy.path("bestDate").asText(""));
                    }
                    legacy.fields().forEachRemaining(field -> {
                        if (!node.has(field.getKey())) node.set(field.getKey(), field.getValue().deepCopy());
                    });
                })));
                if (changed > 0) imported++;
                else skipped++;
            } catch (SQLException | IllegalArgumentException | ArithmeticException e) {
                failed++;
                log.error("导入反应力旧记录失败: user={}", entry.getKey(), e);
            }
        }
        return new MigrationResult(imported, skipped, failed);
    }

    private static void increment(ObjectNode node, String key, long delta) {
        node.put(key, Math.addExact(counter(node, key), delta));
    }

    private static long counter(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null) return 0;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
            throw new IllegalArgumentException("Invalid game counter: " + key);
        return value.longValue();
    }
}
