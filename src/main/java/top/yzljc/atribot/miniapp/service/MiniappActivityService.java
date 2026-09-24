package top.yzljc.atribot.miniapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.repo.UserGameDataRepository;
import top.yzljc.atribot.function.impl.drawitem.LootService;
import top.yzljc.atribot.miniapp.MiniappSessions;
import top.yzljc.atribot.miniapp.MiniappUserSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏与收藏的只读视图；只返回当前会话用户的记录。
 *
 * @Author YZ_Ljc_
 * @ClassName MiniappActivityService
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
public final class MiniappActivityService {
    private static final Logger log = LoggerFactory.getLogger(MiniappActivityService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MiniappProfileService.Connections connections;
    private final Path freeDrawFile;
    private final Clock clock;
    private final Object freeDrawLock;
    private final UserGameDataRepository gameData;

    public record Reaction(long plays, long completed, Long bestMs, Integer bestMisses, String bestDate, Integer rank) {
    }

    public record GameStats(String game, long operations, long correct, long plays, long wins) {
    }

    public record Activity(Reaction reaction, String freeDraw, List<GameStats> games, MiniappUserSettings settings,
                           List<String> unavailable) {
    }

    public record Item(String itemId, String name, int count, long firstAt, long lastAt, String source) {
    }

    public record Gain(long id, long timestamp, String source, int amount) {
    }

    public record Page<T>(List<T> items, int offset, int limit, boolean hasMore, boolean available) {
    }

    @FunctionalInterface
    private interface Row<T> {
        T read(ResultSet rows) throws SQLException;
    }

    public MiniappActivityService() {
        this(DatabaseManager::getConnection, Path.of(Properties.LOOT_FREE_DRAW_RECORD),
                Clock.systemDefaultZone(), LootService.class);
    }

    public MiniappActivityService(MiniappProfileService.Connections connections, Path freeDrawFile,
                           Clock clock, Object freeDrawLock) {
        this.connections = connections;
        this.freeDrawFile = freeDrawFile;
        this.clock = clock;
        this.freeDrawLock = freeDrawLock;
        this.gameData = new UserGameDataRepository(connections::open);
    }

    public Activity load(MiniappSessions.Identity identity) {
        List<String> unavailable = new ArrayList<>();
        Reaction reaction = null;
        String freeDraw = null;
        List<GameStats> games = null;
        MiniappUserSettings settings = null;
        try {
            synchronized (freeDrawLock) {
                freeDraw = freeDraw(identity.userId());
            }
        } catch (IOException | IllegalArgumentException e) {
            unavailable.add("freeDraw");
            log.warn("Miniapp 免费抽卡记录读取失败", e);
        }
        try {
            JsonNode root = gameData.read(identity.userId());
            try {
                reaction = reaction(root.get("reaction"));
            } catch (SQLException | IOException | IllegalArgumentException e) {
                unavailable.add("reaction");
                log.warn("Miniapp 反应力记录读取失败", e);
            }
            try {
                var records = new ArrayList<GameStats>();
                for (var game : UserGameDataRepository.Game.values()) {
                    JsonNode node = root.get(game.name());
                    if (node == null) continue;
                    if (!node.isObject()) throw new IOException("Invalid game data");
                    records.add(new GameStats(game.name(), number(node, "operations"), number(node, "correct"), number(node, "plays"), number(node, "wins")));
                }
                games = List.copyOf(records);
            } catch (IOException e) {
                unavailable.add("games");
                log.warn("Miniapp 游戏统计读取失败", e);
            }
        } catch (SQLException e) {
            unavailable.add("reaction");
            unavailable.add("games");
            log.warn("Miniapp 游戏数据读取失败", e);
        }
        try (Connection con = connections.open(); var ps = con.prepareStatement("SELECT user_settings FROM official_users WHERE user_openId = ?")) {
            ps.setString(1, identity.userId());
            ps.setQueryTimeout(5);
            try (var rows = ps.executeQuery()) {
                settings = MiniappUserSettings.parse(rows.next() ? rows.getString("user_settings") : null);
            }
        } catch (SQLException | IOException e) {
            unavailable.add("settings");
            log.warn("Miniapp 用户设置读取失败", e);
        }
        return new Activity(reaction, freeDraw, games, settings, List.copyOf(unavailable));
    }

    public Page<Item> inventory(MiniappSessions.Identity identity, int offset) {
        return page(identity.userId(), offset, 12,
                "SELECT item_id, display_name, `count`, first_receive_timestamp, last_receive_timestamp, way FROM user_loot_items WHERE user_id = ? AND `count` > 0 ORDER BY last_receive_timestamp DESC, item_id ASC LIMIT ? OFFSET ?",
                r -> new Item(r.getString("item_id"), r.getString("display_name"), r.getInt("count"),
                        r.getLong("first_receive_timestamp"), r.getLong("last_receive_timestamp"), r.getString("way")));
    }

    public Page<Gain> gains(MiniappSessions.Identity identity, int offset) {
        return page(identity.userId(), offset, 8,
                "SELECT id, `timestamp`, way, amount FROM user_coin_gain_logs WHERE user_id = ? ORDER BY id DESC LIMIT ? OFFSET ?",
                r -> new Gain(r.getLong("id"), r.getLong("timestamp"), publicSource(r.getString("way")), r.getInt("amount")));
    }

    private <T> Page<T> page(String userId, int offset, int limit, String sql, Row<T> mapper) {
        if (offset < 0 || offset > 1_000_000) throw new IllegalArgumentException("Invalid offset");
        try (Connection con = connections.open(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, limit + 1);
            ps.setInt(3, offset);
            ps.setQueryTimeout(5);
            List<T> items = new ArrayList<>();
            try (var rows = ps.executeQuery()) {
                while (rows.next()) items.add(mapper.read(rows));
            }
            boolean more = items.size() > limit;
            return new Page<>(List.copyOf(items.subList(0, Math.min(items.size(), limit))), offset, limit, more, true);
        } catch (SQLException e) {
            log.warn("Miniapp 收藏或金粒明细读取失败", e);
            return new Page<>(List.of(), offset, limit, false, false);
        }
    }

    private Reaction reaction(JsonNode user) throws IOException, SQLException {
        if (user == null) return null;
        if (!user.isObject()) throw new IOException("Invalid reaction record");
        long best = number(user, "bestMs"), plays = number(user, "plays"), completed = number(user, "wins");
        if (completed > plays) throw new IOException("Invalid completion count");
        int rank = gameData.reactionRank(best);
        long misses = best > 0 ? number(user, "bestMisses") : 0;
        if (misses > Integer.MAX_VALUE) throw new IOException("Invalid bestMisses");
        return new Reaction(plays, completed, best > 0 ? best : null,
                best > 0 ? (int) misses : null,
                best > 0 ? user.path("bestDate").asText("") : null, best > 0 ? rank : null);
    }

    private String freeDraw(String userId) throws IOException {
        Clock beijing = clock.withZone(ZoneId.of("Asia/Shanghai"));
        if (!LocalTime.now(beijing).isBefore(LocalTime.of(23, 50))) return "locked";
        JsonNode root = readObject(freeDrawFile);
        if (!LocalDate.now(beijing).toString().equals(root.path("date").asText())) return "available";
        if (!root.path("users").isObject()) throw new IOException("Invalid free draw record");
        return root.path("users").has(userId) ? "used" : "available";
    }

    private static JsonNode readObject(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            JsonNode root = JSON.readTree(input);
            if (root == null || !root.isObject()) throw new IOException("Invalid record document");
            return root;
        } catch (NoSuchFileException e) {
            return JSON.createObjectNode();
        }
    }

    private static long number(JsonNode node, String field) throws IOException {
        if (!node.has(field)) return 0;
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0)
            throw new IOException("Invalid " + field);
        return value.asLong();
    }

    private static String publicSource(String source) {
        // This reward code embeds the sharing user's OpenID; it is not part of the viewer's profile.
        return source != null && source.startsWith("atrimeow_share_") ? "share_bot" : source;
    }
}
