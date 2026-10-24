package top.yzljc.atribot.function.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftEnchantmentTable
 * @Created_at 2026/10/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.minecraft
 */
public final class MinecraftEnchantmentTable {
    private final String version;
    private final List<Entry> entries;
    private final List<Preset> presets;

    public record Preset(String key, String id, String name, int enchantability) {
        public boolean isBook() {
            return id.equals("minecraft:enchanted_book");
        }
    }

    public record Enchantment(String id, String name, int level, int maxLevel, boolean treasure, boolean curse) {
        public String displayName() {
            String levelName = switch (level) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                case 6 -> "VI";
                case 7 -> "VII";
                case 8 -> "VIII";
                case 9 -> "IX";
                case 10 -> "X";
                default -> Integer.toString(level);
            };
            return maxLevel == 1 ? name : name + " " + levelName;
        }
    }

    public record Result(String version, Preset item, List<Enchantment> enchantments) {
        public Result {
            enchantments = List.copyOf(enchantments);
        }
    }

    private record Cost(int base, int perLevelAboveFirst) {
        int at(int level) {
            return base + perLevelAboveFirst * (level - 1);
        }
    }

    private record Entry(String id, String name, int weight, int maxLevel, boolean treasure, boolean curse,
                         Set<String> incompatibleWith, Set<String> primaryItems, Cost minCost, Cost maxCost) {
        Enchantment at(int level) {
            return new Enchantment(id, name, level, maxLevel, treasure, curse);
        }
    }

    private record Candidate(Entry entry, int level) {
    }

    /**
     * @param input 完整原版附魔数据流，由调用方关闭
     * @throws IOException              数据读取失败
     * @throws IllegalArgumentException 附魔定义缺失或互斥关系引用了不存在的附魔
     */
    public MinecraftEnchantmentTable(InputStream input) throws IOException {
        JsonNode root = new ObjectMapper().readTree(input);
        version = root.path("minecraft_version").asText();
        var loaded = new ArrayList<Entry>();
        for (var field : root.path("enchantments").properties()) {
            JsonNode data = field.getValue();
            var incompatible = new HashSet<String>();
            for (var id : data.path("incompatible_with")) incompatible.add(id.asText());
            var supported = new HashSet<String>();
            for (var id : data.path("primary_items")) supported.add(id.asText());
            JsonNode definition = data.path("definition");
            var entry = new Entry(field.getKey(), data.path("name").asText(),
                    definition.path("weight").asInt(), definition.path("max_level").asInt(),
                    data.path("treasure").asBoolean(), data.path("curse").asBoolean(), Set.copyOf(incompatible), Set.copyOf(supported),
                    cost(definition.path("min_cost")), cost(definition.path("max_cost")));
            if (!entry.id().matches("minecraft:[a-z0-9_]+") || entry.name().isBlank()
                    || entry.weight() <= 0 || entry.maxLevel() < 1 || supported.isEmpty()) {
                throw new IllegalArgumentException("Invalid Minecraft enchantment: " + entry.id());
            }
            loaded.add(entry);
        }
        if (version.isBlank() || loaded.isEmpty()) throw new IllegalArgumentException("Missing Minecraft enchantments");
        var ids = new HashSet<String>();
        for (var entry : loaded) ids.add(entry.id());
        for (var entry : loaded) {
            if (!ids.containsAll(entry.incompatibleWith())) {
                throw new IllegalArgumentException("Unknown incompatible enchantment: " + entry.id());
            }
        }
        var byId = new HashMap<String, Entry>();
        for (var entry : loaded) byId.put(entry.id(), entry);
        var tableEntries = new ArrayList<Entry>();
        for (var id : root.path("table_enchantments")) {
            Entry entry = byId.get(id.asText());
            if (entry == null || tableEntries.contains(entry))
                throw new IllegalArgumentException("Invalid enchanting table pool");
            tableEntries.add(entry);
        }
        if (tableEntries.isEmpty()) throw new IllegalArgumentException("Missing enchanting table pool");
        entries = List.copyOf(tableEntries);
        var items = new ArrayList<Preset>();
        var keys = new HashSet<String>();
        for (var data : root.path("presets")) {
            var preset = new Preset(data.path("key").asText(), data.path("id").asText(), data.path("name").asText(),
                    data.path("enchantability").asInt());
            if (preset.key().isBlank() || !keys.add(preset.key()) || preset.name().isBlank()
                    || !preset.id().matches("minecraft:[a-z0-9_]+")
                    || preset.enchantability() < 1
                    || (!preset.isBook() && entries.stream().noneMatch(e -> e.primaryItems().contains(preset.id())))) {
                throw new IllegalArgumentException("Invalid enchantment preset: " + preset.id());
            }
            items.add(preset);
        }
        if (items.stream().noneMatch(Preset::isBook)) throw new IllegalArgumentException("Missing book preset");
        presets = List.copyOf(items);
    }

    private static Cost cost(JsonNode data) {
        if (!data.has("base") || !data.has("per_level_above_first")) {
            throw new IllegalArgumentException("Missing enchantment cost");
        }
        return new Cost(data.path("base").asInt(), data.path("per_level_above_first").asInt());
    }

    public static MinecraftEnchantmentTable load() {
        try (var input = MinecraftEnchantmentTable.class.getResourceAsStream("/minecraft/enchantments-26.3.json")) {
            if (input == null) throw new IllegalStateException("缺少 Minecraft 附魔数据");
            return new MinecraftEnchantmentTable(input);
        } catch (IOException e) {
            throw new IllegalStateException("Minecraft 附魔数据加载失败", e);
        }
    }

    /**
     * @param random 随机数生成器
     * @return 附魔书结果，包含不重复且相互兼容的原版附魔
     */
    public Result roll(RandomGenerator random) {
        return roll(findPreset("book"), random);
    }

    public List<Preset> presets() {
        return presets;
    }

    /**
     * @param value 预设键名、物品 ID 或中文名称
     * @return 匹配的物品预设，未找到时返回 null
     */
    public Preset findPreset(String value) {
        if (value == null) return null;
        String key = value.trim().toLowerCase(Locale.ROOT);
        return presets.stream().filter(p -> p.key().equals(key) || p.id().equals(key)
                || p.id().substring("minecraft:".length()).equals(key) || p.name().equals(key)).findFirst().orElse(null);
    }

    /**
     * @param preset 当前数据集中的物品预设
     * @param random 随机数生成器
     * @return Java 版 15 个书架、30 级第三档的附魔结果，包含原版附魔能力修正及附魔书移除一条的规则
     * @throws IllegalArgumentException 预设不属于当前数据集
     */
    public Result roll(Preset preset, RandomGenerator random) {
        if (preset == null || !presets.contains(preset))
            throw new IllegalArgumentException("Unknown enchantment preset");
        int bound = preset.enchantability() / 4 + 1;
        int power = 30 + 1 + random.nextInt(bound) + random.nextInt(bound);
        float variation = (random.nextFloat() + random.nextFloat() - 1.0f) * 0.15f;
        power = Math.max(1, Math.round(power + power * variation));
        var candidates = new ArrayList<Candidate>();
        for (var entry : entries) {
            if (!preset.isBook() && !entry.primaryItems().contains(preset.id())) continue;
            for (int level = entry.maxLevel(); level >= 1; level--) {
                if (power >= entry.minCost().at(level) && power <= entry.maxCost().at(level)) {
                    candidates.add(new Candidate(entry, level));
                    break;
                }
            }
        }
        var result = new ArrayList<Enchantment>();
        if (!candidates.isEmpty()) {
            Candidate chosen = choose(candidates, random);
            result.add(chosen.entry().at(chosen.level()));
            while (random.nextInt(50) <= power) {
                Entry last = chosen.entry();
                candidates.removeIf(candidate -> last.id().equals(candidate.entry().id())
                        || last.incompatibleWith().contains(candidate.entry().id())
                        || candidate.entry().incompatibleWith().contains(last.id()));
                if (candidates.isEmpty()) break;
                chosen = choose(candidates, random);
                result.add(chosen.entry().at(chosen.level()));
                power /= 2;
            }
        }
        if (preset.isBook() && result.size() > 1) result.remove(random.nextInt(result.size()));
        return new Result(version, preset, result);
    }

    private static Candidate choose(List<Candidate> candidates, RandomGenerator random) {
        int weight = random.nextInt(candidates.stream().mapToInt(candidate -> candidate.entry().weight()).sum());
        for (var candidate : candidates) {
            weight -= candidate.entry().weight();
            if (weight < 0) return candidate;
        }
        throw new IllegalStateException("Invalid enchantment weights");
    }
}
