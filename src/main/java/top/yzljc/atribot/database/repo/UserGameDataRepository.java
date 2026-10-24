package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.database.DatabaseManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
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

    public enum Game {minesweeper, sound, connect4, roulette, rsp, fishing, tictactoe}

    private static final Map<String, List<String>> COUNTER_FIELDS = Map.of(
            "minesweeper", List.of("participations", "plays", "wins", "losses", "operations", "digs", "mineHits", "flagsPlaced", "flagsRemoved"),
            "sound", List.of("operations", "correct", "wrong"),
            "connect4", List.of("participations", "plays", "wins", "losses", "draws", "moves", "normalWins", "timeoutWins", "timeoutPlays", "moveTimeouts"),
            "roulette", List.of("participations", "plays", "wins", "losses", "actions", "selfTargets", "otherTargets", "timeouts"),
            "rsp", List.of("participations", "plays", "wins", "losses", "draws", "rock", "scissors", "paper"),
            "reaction", List.of("plays", "wins", "hits", "misses", "abandoned", "timeouts", "timedCompletions", "totalCompletedMs"),
            "fishing", List.of("plays", "fish", "junk", "treasure", "gold", "enchanted"),
            "tictactoe", List.of("plays", "wins", "losses", "draws", "moves", "normalWins", "forfeitWins", "timeoutWins", "forfeits", "moveTimeouts"));

    /**
     * @param game 游戏数据键
     * @return 可用于统计展示的累计字段，未知游戏返回空列表
     */
    public static List<String> counterFields(String game) {
        return COUNTER_FIELDS.getOrDefault(game, List.of());
    }

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

    public record Counters(String userId, Map<String, Long> values) {
        public Counters {
            values = Map.copyOf(values);
            if (values.entrySet().stream().anyMatch(entry ->
                    !entry.getKey().matches("[a-z][a-zA-Z0-9]{0,31}") || entry.getValue() < 0)) {
                throw new IllegalArgumentException("Invalid game counters");
            }
        }
    }

    public enum ReactionOutcome {COMPLETED, TIMED_OUT, ABANDONED}

    public UserGameDataRepository(Connections connections) {
        this.connections = connections;
    }

    public static UserGameDataRepository instance() {
        return INSTANCE;
    }

    public static void init() {
        try (var con = INSTANCE.connections.open(); var ps = con.createStatement()) {
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
            throw new IllegalStateException("游戏统计事件表初始化失败", e);
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
                if (game == Game.sound) {
                    increment(node, "correct", delta.correct());
                    increment(node, "wrong", delta.operations() - delta.correct());
                } else {
                    increment(node, "digs", delta.operations());
                }
            } else {
                increment(node, "plays", delta.plays());
                increment(node, "wins", delta.wins());
            }
        })).toList());
    }

    /**
     * 按事件累计用户指标；同一游戏、事件和用户的重复记录不再累计。
     *
     * @param game     游戏类型
     * @param eventId  事件唯一标识
     * @param counters 用户指标增量，多人记录在同一事务中提交
     */
    public static void recordCounters(Game game, String eventId, List<Counters> counters) {
        try {
            INSTANCE.appendCounters(game, eventId, counters);
        } catch (SQLException | RuntimeException e) {
            log.error("游戏统计写入失败: game={}, event={}", game, eventId, e);
        }
    }

    public int appendCounters(Game game, String eventId, List<Counters> counters) throws SQLException {
        if (game == null) throw new IllegalArgumentException("Invalid game");
        return mutate(game.name(), eventId, counters.stream().map(delta ->
                        new Update(delta.userId(), node -> delta.values().forEach((key, value) -> increment(node, key, value))))
                .toList());
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
                    ObjectNode root = lockData(con, update.userId());
                    try (var ps = con.prepareStatement("INSERT INTO user_game_stat_events (game, event_id, user_id) VALUES (?, ?, ?)")) {
                        ps.setQueryTimeout(5);
                        ps.setString(1, game);
                        ps.setString(2, eventId);
                        ps.setString(3, update.userId());
                        try {
                            ps.executeUpdate();
                        } catch (SQLException duplicate) {
                            if ((duplicate.getErrorCode() == 1062 && "23000".equals(duplicate.getSQLState()))
                                    || "23505".equals(duplicate.getSQLState())) continue;
                            throw duplicate;
                        }
                    }
                    JsonNode existing = root.get(game);
                    if (existing != null && !(existing instanceof ObjectNode))
                        throw new SQLException("Game data is not an object: " + game);
                    ObjectNode node = existing == null ? root.putObject(game) : (ObjectNode) existing;
                    if (game.equals("minesweeper") && !node.has("digs")) node.put("digs", counter(node, "operations"));
                    update.change().accept(node);
                    saveData(con, update.userId(), root);
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

    /**
     * @param con    调用方事务连接
     * @param userId 用户标识
     * @return 持有玩家游戏数据行锁的 JSON；记录不存在时创建，不创建账号或权限记录
     * @throws SQLException 加锁或读取失败
     */
    static ObjectNode lockData(Connection con, String userId) throws SQLException {
        BotUserDataRepository.ensureUser(con, userId);
        try (var ps = con.prepareStatement("SELECT game_data FROM bot_userdata WHERE user_id = ? FOR UPDATE")) {
            ps.setQueryTimeout(5);
            ps.setString(1, userId);
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing player game data row");
                return parse(rows.getString("game_data"));
            }
        }
    }

    static void saveData(Connection con, String userId, ObjectNode root) throws SQLException {
        try (var ps = con.prepareStatement("UPDATE bot_userdata SET game_data = ? WHERE user_id = ?")) {
            ps.setQueryTimeout(5);
            ps.setString(1, root.toString());
            ps.setString(2, userId);
            if (ps.executeUpdate() != 1) throw new SQLException("Game data was not saved");
        }
    }

    public ObjectNode read(String userId) throws SQLException {
        try (var con = connections.open(); var ps = con.prepareStatement("SELECT game_data FROM bot_userdata WHERE user_id = ?")) {
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
            if (root instanceof ObjectNode object) {
                if (object.get("minesweeper") instanceof ObjectNode minesweeper && !minesweeper.has("digs")) {
                    minesweeper.put("digs", counter(minesweeper, "operations"));
                }
                if (object.get("sound") instanceof ObjectNode sound && !sound.has("wrong")) {
                    long wrong = Math.subtractExact(counter(sound, "operations"), counter(sound, "correct"));
                    if (wrong < 0) throw new SQLException("Invalid sound statistics");
                    sound.put("wrong", wrong);
                }
                return object;
            }
            throw new SQLException("Game data is not a JSON object");
        } catch (IOException e) {
            throw new SQLException("Invalid game JSON; refusing to overwrite", e);
        }
    }

    public int reactionRank(long bestMs) throws SQLException {
        return reactionRank(bestMs, null);
    }

    /**
     * 按本局用时计算名次时，排除玩家自己的历史最好成绩。
     */
    public int reactionRank(long elapsedMs, String excludedUserId) throws SQLException {
        if (elapsedMs <= 0) return 0;
        String query = """
                SELECT COUNT(*) AS faster FROM bot_userdata
                WHERE CAST(JSON_UNQUOTE(JSON_EXTRACT(game_data, '$.reaction.bestMs')) AS UNSIGNED) BETWEEN 1 AND ?
                """ + (excludedUserId == null ? "" : " AND user_id <> ?");
        try (var con = connections.open(); var ps = con.prepareStatement(query)) {
            ps.setQueryTimeout(5);
            ps.setLong(1, elapsedMs - 1);
            if (excludedUserId != null) ps.setString(2, excludedUserId);
            try (var rows = ps.executeQuery()) {
                rows.next();
                return Math.toIntExact(rows.getLong("faster") + 1);
            }
        }
    }

    public boolean recordReaction(String userId, String eventId, boolean completed, long elapsedMs, int misses, LocalDate date) throws SQLException {
        return recordReaction(userId, eventId, completed, elapsedMs, misses, date, null, 0);
    }

    /**
     * 记录反应力测试结算，保留旧场次和最佳成绩字段，并累计细分结果。
     *
     * @param userId    游戏发起者标识
     * @param eventId   本局唯一标识
     * @param outcome   完成、超时或主动放弃
     * @param elapsedMs 本局计时毫秒数，尚未开始计时可为零
     * @param hits      本局正确点击次数
     * @param misses    本局失误次数
     * @param date      结算日期
     * @return 本次写入是否刷新个人最佳成绩
     * @throws SQLException 数据读取或事务提交失败
     */
    public boolean recordReaction(String userId, String eventId, ReactionOutcome outcome, long elapsedMs,
                                  int hits, int misses, LocalDate date) throws SQLException {
        if (outcome == null || hits < 0) throw new IllegalArgumentException("Invalid reaction result");
        return recordReaction(userId, eventId, outcome == ReactionOutcome.COMPLETED, elapsedMs, misses, date, outcome, hits);
    }

    private boolean recordReaction(String userId, String eventId, boolean completed, long elapsedMs, int misses,
                                   LocalDate date, ReactionOutcome outcome, int hits) throws SQLException {
        if (elapsedMs < 0 || misses < 0 || (completed && elapsedMs == 0))
            throw new IllegalArgumentException("Invalid reaction result");
        boolean[] newBest = {false};
        int changed = mutate("reaction", eventId, List.of(new Update(userId, node -> {
            newBest[0] = false;
            increment(node, "plays", 1);
            increment(node, "wins", completed ? 1 : 0);
            if (outcome != null) {
                increment(node, "hits", hits);
                increment(node, "misses", misses);
                switch (outcome) {
                    case COMPLETED -> {
                        increment(node, "timedCompletions", 1);
                        increment(node, "totalCompletedMs", elapsedMs);
                    }
                    case TIMED_OUT -> increment(node, "timeouts", 1);
                    case ABANDONED -> increment(node, "abandoned", 1);
                }
            }
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
//    public MigrationResult migrateLegacyReaction(Path file) throws IOException {
//        if (!Files.exists(file)) return new MigrationResult(0, 0, 0);
//        JsonNode root;
//        try (var input = Files.newInputStream(file)) {
//            root = JSON.readTree(input);
//        }
//        if (!(root instanceof ObjectNode)) throw new IOException("Invalid legacy reaction document");
//        int imported = 0, skipped = 0, failed = 0;
//        var entries = root.fields();
//        while (entries.hasNext()) {
//            var entry = entries.next();
//            try {
//                if (!(entry.getValue() instanceof ObjectNode legacy))
//                    throw new IllegalArgumentException("Invalid legacy user");
//                long plays = counter(legacy, "plays"), wins = counter(legacy, "wins"), best = counter(legacy, "bestMs");
//                long misses = counter(legacy, "bestMisses");
//                if (wins > plays || misses > Integer.MAX_VALUE)
//                    throw new IllegalArgumentException("Invalid legacy counters");
//                int changed = mutate("reaction", "legacy-click-train-v1", List.of(new Update(entry.getKey(), node -> {
//                    long currentBest = counter(node, "bestMs");
//                    increment(node, "plays", plays);
//                    increment(node, "wins", wins);
//                    if (best > 0 && (currentBest == 0 || best < currentBest)) {
//                        node.put("bestMs", best).put("bestMisses", misses).put("bestDate", legacy.path("bestDate").asText(""));
//                    }
//                    legacy.fields().forEachRemaining(field -> {
//                        if (!node.has(field.getKey())) node.set(field.getKey(), field.getValue().deepCopy());
//                    });
//                })));
//                if (changed > 0) imported++;
//                else skipped++;
//            } catch (SQLException | IllegalArgumentException | ArithmeticException e) {
//                failed++;
//                log.error("导入反应力旧记录失败: user={}", entry.getKey(), e);
//            }
//        }
//        return new MigrationResult(imported, skipped, failed);
//    }
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
