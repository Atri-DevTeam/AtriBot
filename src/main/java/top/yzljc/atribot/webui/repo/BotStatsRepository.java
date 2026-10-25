package top.yzljc.atribot.webui.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.repo.UserGameDataRepository;
import top.yzljc.atribot.function.impl.drawitem.LootService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName BotStatsRepository
 * @Created_at 2026/10/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.repo
 */
@Slf4j
public final class BotStatsRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> GAMES = List.of("minesweeper", "sound", "connect4", "roulette", "rsp", "reaction", "fishing", "tictactoe");
    private final UserGameDataRepository.Connections connections;
    private final Supplier<Catalog> catalog;
    private final Supplier<LocalDate> today;

    public record Catalog(Set<String> items, Set<String> normalItems, long fetchedAt) {
        public Catalog {
            items = Set.copyOf(items);
            normalItems = Set.copyOf(normalItems);
        }
    }

    public record Snapshot(long generatedAt, Map<String, BotStatsSection> games, BotStatsSection loot,
                           BotStatsSection sign, Map<String, Long> registrations,
                           List<String> unavailable) {
    }

    public BotStatsRepository() {
        this(DatabaseManager::getConnection, () -> {
            var items = LootService.getCatalog(false);
            return new Catalog(items.stream().map(LootService.LootCatalogItem::itemId).collect(Collectors.toSet()),
                    items.stream().filter(item -> !item.special()).map(LootService.LootCatalogItem::itemId).collect(Collectors.toSet()),
                    LootService.getCatalogFetchedAt());
        }, () -> LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    public BotStatsRepository(UserGameDataRepository.Connections connections, Supplier<Catalog> catalog, Supplier<LocalDate> today) {
        this.connections = connections;
        this.catalog = catalog;
        this.today = today;
    }

    public Snapshot load() {
        List<String> unavailable = new ArrayList<>();
        var games = section("games", unavailable, this::games);
        var loot = section("loot", unavailable, () -> loot(unavailable));
        var sign = section("sign", unavailable, this::sign);
        var registrations = section("registrations", unavailable, () -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            query("SELECT COUNT(*) AS total FROM official_users", rows -> counts.put("users", rows.getLong("total")));
            query("SELECT COUNT(*) AS total FROM official_groups", rows -> counts.put("groups", rows.getLong("total")));
            return Collections.unmodifiableMap(counts);
        });
        return new Snapshot(System.currentTimeMillis(), games, loot, sign, registrations, List.copyOf(unavailable));
    }

    private Map<String, BotStatsSection> games() throws Exception {
        Map<String, Map<String, Map<String, Object>>> players = new LinkedHashMap<>();
        GAMES.forEach(game -> players.put(game, new LinkedHashMap<>()));
        query("SELECT user_id, game_data FROM bot_userdata WHERE game_data IS NOT NULL", rows -> {
            var root = UserGameDataRepository.parse(rows.getString("game_data"));
            for (String game : GAMES) {
                var node = root.get(game);
                if (node == null) continue;
                if (!node.isObject()) throw new SQLException("Invalid game statistics");
                Map<String, Object> player = identity(rows.getString("user_id"));
                for (String metric : UserGameDataRepository.counterFields(game))
                    player.put(metric, counter(node, metric));
                if (game.equals("reaction")) {
                    player.put("bestMs", counter(node, "bestMs"));
                    player.put("bestMisses", counter(node, "bestMisses"));
                    String date = node.path("bestDate").asText("");
                    player.put("bestDate", date.matches("\\d{4}-\\d{2}-\\d{2}") ? date : null);
                }
                rates(game, player);
                if (UserGameDataRepository.counterFields(game).stream().anyMatch(metric -> number(player, metric) > 0) || number(player, "bestMs") > 0) {
                    players.get(game).put(rows.getString("user_id"), player);
                }
            }
        });
        Map<String, Long> rounds = new LinkedHashMap<>();
        query("SELECT game, COUNT(DISTINCT event_id) AS total FROM user_game_stat_events "
                        + "WHERE event_id NOT LIKE '%:%' AND event_id NOT LIKE 'legacy-%' GROUP BY game",
                rows -> rounds.put(rows.getString("game"), rows.getLong("total")));
        Map<String, BotStatsSection> result = new LinkedHashMap<>();
        for (String game : GAMES) {
            var participants = players.get(game);
            Map<String, Object> totals = new LinkedHashMap<>();
            totals.put("players", participants.size());
            totals.put("recordedRounds", rounds.getOrDefault(game, 0L));
            for (String metric : UserGameDataRepository.counterFields(game))
                totals.put(metric, sum(participants, metric));
            rates(game, totals);
            var metrics = new ArrayList<>(UserGameDataRepository.counterFields(game));
            if (game.equals("sound")) metrics.add("accuracy");
            else if (game.equals("fishing")) metrics.add("treasureRate");
            else metrics.add(game.equals("reaction") ? "completionRate" : "winRate");
            if (game.equals("reaction")) {
                metrics.add("bestMs");
                metrics.add("averageCompletedMs");
                var best = participants.values().stream().mapToLong(p -> number(p, "bestMs")).filter(value -> value > 0).min();
                totals.put("bestMs", best.isPresent() ? best.getAsLong() : null);
            }
            result.put(game, new BotStatsSection(totals, participants, metrics, Set.of("bestMs", "averageCompletedMs")));
        }
        return Collections.unmodifiableMap(result);
    }

    private BotStatsSection loot(List<String> unavailable) throws Exception {
        Map<String, Map<String, Object>> players = new LinkedHashMap<>();
        Set<String> ownedGlobally = new LinkedHashSet<>();
        Catalog current = section("loot.catalog", unavailable, catalog::get);
        boolean hasCatalog = current != null && current.fetchedAt() > 0;
        if (!hasCatalog && !unavailable.contains("loot.catalog")) unavailable.add("loot.catalog");
        query("SELECT user_id, coins, loots FROM user_loots", rows -> {
            var player = identity(rows.getString("user_id"));
            player.put("coins", rows.getLong("coins"));
            var items = lootCounts(rows.getString("loots"));
            long owned = 0;
            for (long count : items.values()) owned = Math.addExact(owned, count);
            player.put("ownedCards", owned);
            player.put("collectedTypes", items.size());
            ownedGlobally.addAll(items.keySet());
            collection(player, items.keySet(), hasCatalog ? current : null);
            players.put(rows.getString("user_id"), player);
        });
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("users", players.size());
        totals.put("collectors", players.values().stream().filter(p -> number(p, "ownedCards") > 0).count());
        totals.put("coins", sum(players, "coins"));
        totals.put("ownedCards", sum(players, "ownedCards"));
        totals.put("collectedTypes", ownedGlobally.size());
        totals.put("catalogFetchedAt", current == null ? 0 : current.fetchedAt());
        collection(totals, ownedGlobally, hasCatalog ? current : null);
        return new BotStatsSection(totals, players,
                List.of("coins", "ownedCards", "collectedTypes", "collectionCompletion", "normalCollectionCompletion"), Set.of());
    }

    private BotStatsSection sign() throws Exception {
        Map<String, Map<String, Object>> players = new LinkedHashMap<>();
        query("SELECT s.user_open_id, s.total_count, s.total_coins, s.last_check_in_date, COALESCE(l.coins, 0) AS coins "
                + "FROM check_in_total s LEFT JOIN user_loots l ON l.user_id = s.user_open_id", rows -> {
            var player = identity(rows.getString("user_open_id"));
            player.put("totalCount", rows.getLong("total_count"));
            player.put("totalCoins", rows.getLong("total_coins"));
            player.put("coins", rows.getLong("coins"));
            player.put("lastCheckInDate", rows.getString("last_check_in_date"));
            players.put(rows.getString("user_open_id"), player);
        });
        LocalDate date = today.get();
        long[] daily = {0, 0};
        query("SELECT user_open_id, check_in_time, coins FROM check_in_daily WHERE check_in_date = ? ORDER BY id", rows -> {
            daily[0]++;
            daily[1] = Math.addExact(daily[1], rows.getLong("coins"));
            var player = players.get(rows.getString("user_open_id"));
            if (player != null) {
                player.put("todayRank", daily[0]);
                player.put("todayCheckInTime", rows.getLong("check_in_time"));
                player.put("todayCoins", rows.getLong("coins"));
            }
        }, java.sql.Date.valueOf(date));
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("todayDate", date.toString());
        totals.put("totalUsers", players.values().stream().filter(p -> number(p, "totalCount") > 0).count());
        totals.put("totalCount", sum(players, "totalCount"));
        totals.put("totalCoins", sum(players, "totalCoins"));
        totals.put("todayCount", daily[0]);
        totals.put("todayCoins", daily[1]);
        return new BotStatsSection(totals, players, List.of("totalCount", "totalCoins", "todayCheckInTime"), Set.of("todayCheckInTime"));
    }

    private static void collection(Map<String, Object> target, Set<String> owned, Catalog catalog) {
        if (catalog == null) {
            for (String key : List.of("catalogTypes", "normalCatalogTypes", "collectedCatalogTypes", "collectedNormalTypes",
                    "collectionCompletion", "normalCollectionCompletion"))
                target.put(key, null);
            return;
        }
        long collected = owned.stream().filter(catalog.items()::contains).count();
        long normal = owned.stream().filter(catalog.normalItems()::contains).count();
        target.put("catalogTypes", catalog.items().size());
        target.put("normalCatalogTypes", catalog.normalItems().size());
        target.put("collectedCatalogTypes", collected);
        target.put("collectedNormalTypes", normal);
        target.put("collectionCompletion", percent(collected, catalog.items().size()));
        target.put("normalCollectionCompletion", percent(normal, catalog.normalItems().size()));
    }

    private static Map<String, Long> lootCounts(String raw) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return counts;
        JsonNode root = JSON.readTree(raw);
        if (!root.isArray()) throw new SQLException("Invalid loot statistics");
        for (JsonNode node : root) {
            String id = node.path("item_id").asText("");
            if (id.isBlank()) throw new SQLException("Missing loot item identifier");
            long count = node.has("count") ? Math.max(1, counter(node, "count")) : 1;
            counts.merge(id, count, Math::addExact);
        }
        return counts;
    }

    private static void rates(String game, Map<String, Object> data) {
        if (game.equals("sound")) data.put("accuracy", percent(number(data, "correct"), number(data, "operations")));
        else if (game.equals("fishing")) data.put("treasureRate", percent(number(data, "treasure"), number(data, "plays")));
        else
            data.put(game.equals("reaction") ? "completionRate" : "winRate", percent(number(data, "wins"), number(data, "plays")));
        if (game.equals("reaction")) data.put("averageCompletedMs", number(data, "timedCompletions") == 0 ? null
                : (double) number(data, "totalCompletedMs") / number(data, "timedCompletions"));
    }

    private static Double percent(long numerator, long denominator) {
        return denominator == 0 ? null : 100.0 * numerator / denominator;
    }

    private static Map<String, Object> identity(String id) {
        var result = new LinkedHashMap<String, Object>();
        result.put("userId", id);
        return result;
    }

    private static long counter(JsonNode node, String key) throws SQLException {
        JsonNode value = node.get(key);
        if (value == null) return 0;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
            throw new SQLException("Invalid game counter");
        return value.longValue();
    }

    private static long number(Map<String, Object> data, String key) {
        return data.get(key) instanceof Number value ? value.longValue() : 0;
    }

    private static long sum(Map<String, Map<String, Object>> players, String key) {
        long total = 0;
        for (var player : players.values()) total = Math.addExact(total, number(player, key));
        return total;
    }

    private void query(String sql, Row reader, Object... parameters) throws Exception {
        try (Connection connection = connections.open(); var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            statement.setQueryTimeout(15);
            statement.setFetchSize(500);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) reader.accept(rows);
            }
        }
    }

    private static <T> T section(String name, List<String> unavailable, Query<T> query) {
        try {
            return query.get();
        } catch (Exception e) {
            log.warn("聚合统计读取失败: section={}", name, e);
            unavailable.add(name);
            return null;
        }
    }

    @FunctionalInterface
    private interface Row {
        void accept(ResultSet rows) throws Exception;
    }

    @FunctionalInterface
    private interface Query<T> {
        T get() throws Exception;
    }
}
