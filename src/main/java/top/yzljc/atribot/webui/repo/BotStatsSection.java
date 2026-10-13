package top.yzljc.atribot.webui.repo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName BotStatsSection
 * @Created_at 2026/10/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.repo
 */
public final class BotStatsSection {
    public record Ranking(int rank, String userId, Number value) {
    }

    private final Map<String, Object> totals;
    private final Map<String, Map<String, Object>> players;
    private final Map<String, List<Ranking>> leaderboards = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> ranks = new LinkedHashMap<>();

    public BotStatsSection(Map<String, Object> totals, Map<String, Map<String, Object>> players,
                           List<String> metrics, Set<String> ascending) {
        this.totals = Collections.unmodifiableMap(new LinkedHashMap<>(totals));
        Map<String, Map<String, Object>> copied = new LinkedHashMap<>();
        players.forEach((id, data) -> copied.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(data))));
        this.players = Collections.unmodifiableMap(copied);
        for (String metric : metrics) {
            Comparator<Map.Entry<String, Map<String, Object>>> comparator = Comparator.comparing(
                    entry -> decimal((Number) entry.getValue().get(metric)));
            if (!ascending.contains(metric)) comparator = comparator.reversed();
            var sorted = players.entrySet().stream()
                    .filter(entry -> entry.getValue().get(metric) instanceof Number value && decimal(value).signum() > 0)
                    .sorted(comparator.thenComparing(Map.Entry::getKey)).toList();
            var entries = new ArrayList<Ranking>();
            BigDecimal previous = null;
            int position = 0, rank = 0;
            for (var entry : sorted) {
                position++;
                Number value = (Number) entry.getValue().get(metric);
                BigDecimal score = decimal(value);
                if (previous == null || previous.compareTo(score) != 0) rank = position;
                previous = score;
                ranks.computeIfAbsent(entry.getKey(), _ -> new LinkedHashMap<>()).put(metric, rank);
                entries.add(new Ranking(rank, (String) entry.getValue().get("userId"), value));
            }
            leaderboards.put(metric, List.copyOf(entries));
        }
    }

    public Map<String, Object> view() {
        return Map.of("totals", totals, "players", players.keySet().stream().sorted().map(this::player).toList(),
                "leaderboards", Collections.unmodifiableMap(leaderboards));
    }

    public Map<String, Object> player(String id) {
        var data = players.get(id);
        if (data == null) return null;
        var result = new LinkedHashMap<>(data);
        result.put("ranks", Collections.unmodifiableMap(ranks.getOrDefault(id, Map.of())));
        return Collections.unmodifiableMap(result);
    }

    private static BigDecimal decimal(Number number) {
        return new BigDecimal(number.toString());
    }
}
