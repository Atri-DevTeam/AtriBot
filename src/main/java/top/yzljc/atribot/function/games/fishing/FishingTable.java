package top.yzljc.atribot.function.games.fishing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import top.yzljc.atribot.function.games.fishing.FishingResult.Durability;
import top.yzljc.atribot.function.games.fishing.FishingResult.Enchantment;
import top.yzljc.atribot.function.games.fishing.FishingResult.Item;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import java.util.random.RandomGenerator;

/**
 * @Author YZ_Ljc_
 * @ClassName FishingTable
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games.fishing
 */
public final class FishingTable {
    private static final List<String> CATEGORIES = List.of("fish", "junk", "treasure");
    private final Definition definition;
    private final Map<Integer, Double> rewardProbabilities;

    public record Rules(String timezone, int dailyLimit, int luckOfTheSeaLevel, int playerLuck,
                        boolean openWater, String biomeSelection) {
    }

    public record Category(int baseWeight, int quality) {
    }

    public record Entry(String id, String name, int weight, List<Item> items, int gold,
                        String potion, String durability, String enchantment) {
    }

    public record Biome(String id, String name, int weight, List<Entry> appendJunk, List<Entry> appendTreasure) {
    }

    public record Damage(int maxDurability, float remainingFractionMin, float remainingFractionMax) {
    }

    public record Cost(int base, int perLevelAboveFirst) {
        int at(int level) {
            return base + perLevelAboveFirst * (level - 1);
        }
    }

    public record EnchantmentDefinition(String name, int weight, int maxLevel, Cost minCost, Cost maxCost,
                                        List<String> allowedItems, List<String> incompatibleWith) {
    }

    public record Enchantments(int baseLevel, int enchantability, int goldPerNonCurseLevel,
                               int mendingExtraGold, int maxBonusGold, Set<String> curseIds,
                               Map<String, EnchantmentDefinition> definitions) {
    }

    public record Definition(String minecraftVersion, Rules rules, Map<String, Category> categories,
                             Map<String, List<Entry>> commonLoot, List<Biome> biomes,
                             Map<String, Damage> durability, Enchantments enchantments) {
    }

    private record Candidate(String id, EnchantmentDefinition definition, int level) {
    }

    public FishingTable(InputStream input) throws IOException {
        definition = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .readValue(Objects.requireNonNull(input, "Fishing table resource missing"), Definition.class);
        validate();
        rewardProbabilities = calculateRewardProbabilities();
    }

    public static FishingTable load() {
        try (var input = FishingTable.class.getResourceAsStream("/games/daily-fishing.json")) {
            return new FishingTable(input);
        } catch (IOException e) {
            throw new IllegalStateException("钓鱼奖励表加载失败", e);
        }
    }

    public ZoneId zone() {
        return ZoneId.of(definition.rules().timezone());
    }

    public FishingResult roll(LocalDate date, RandomGenerator random) {
        Biome biome = choose(definition.biomes(), Biome::weight, random);
        String category = choose(CATEGORIES, this::categoryWeight, random);
        var entries = new ArrayList<>(definition.commonLoot().get(category));
        if (category.equals("junk")) entries.addAll(biome.appendJunk());
        if (category.equals("treasure")) entries.addAll(biome.appendTreasure());
        Entry entry = choose(entries, Entry::weight, random);
        Durability durability = null;
        if (entry.durability() != null) {
            Damage damage = definition.durability().get(entry.durability());
            float fraction = damage.remainingFractionMin()
                    + random.nextFloat() * (damage.remainingFractionMax() - damage.remainingFractionMin());
            int remaining = damage.maxDurability() - (int) Math.floor((1.0F - fraction) * damage.maxDurability());
            durability = new Durability(remaining, damage.maxDurability());
        }
        List<Enchantment> enchantments = entry.enchantment() == null ? List.of() : enchant(entry.enchantment(), random);
        int originalGold = Math.addExact(entry.gold(), bonus(enchantments));
        int baseGold = entry.gold() / 2;
        int bonusGold = originalGold / 2 - baseGold;
        return new FishingResult(date.toString(), biome.id(), biome.name(), category, entry.id(), entry.name(),
                entry.items(), entry.potion(), durability, enchantments, baseGold, bonusGold, luck(originalGold));
    }

    /**
     * 按原始奖励在奖池中的加权排名计算幸运值，同值条目取排名区间的中点
     *
     * @param originalGold 减半前的渔获奖励，包含附魔加成
     * @return 0 至 100 的幸运值，不参与出货或奖励计算
     */
    public int luck(int originalGold) {
        double percentile = 0;
        for (var reward : rewardProbabilities.entrySet()) {
            if (reward.getKey() > originalGold) break;
            percentile += reward.getValue() * (reward.getKey() == originalGold ? 0.5 : 1);
        }
        return Math.clamp(Math.round(percentile * 100), 0, 100);
    }

    private Map<Integer, Double> calculateRewardProbabilities() {
        var probabilities = new TreeMap<Integer, Double>();
        int biomeWeight = definition.biomes().stream().mapToInt(Biome::weight).sum();
        int totalCategoryWeight = CATEGORIES.stream().mapToInt(this::categoryWeight).sum();
        for (var biome : definition.biomes()) {
            for (String category : CATEGORIES) {
                var entries = new ArrayList<>(definition.commonLoot().get(category));
                if (category.equals("junk")) entries.addAll(biome.appendJunk());
                if (category.equals("treasure")) entries.addAll(biome.appendTreasure());
                int entryWeight = entries.stream().mapToInt(Entry::weight).sum();
                double probability = (double) biome.weight() / biomeWeight
                        * categoryWeight(category) / totalCategoryWeight;
                for (var entry : entries) {
                    probabilities.merge(entry.gold(), probability * entry.weight() / entryWeight, Double::sum);
                }
            }
        }
        return java.util.Collections.unmodifiableSortedMap(probabilities);
    }

    int categoryWeight(String category) {
        if (category.equals("treasure") && !definition.rules().openWater()) return 0;
        Category weights = definition.categories().get(category);
        int luck = definition.rules().luckOfTheSeaLevel() + definition.rules().playerLuck();
        return Math.max(0, Math.addExact(weights.baseWeight(), Math.multiplyExact(weights.quality(), luck)));
    }

    List<Enchantment> enchant(String item, RandomGenerator random) {
        Enchantments settings = definition.enchantments();
        int range = settings.enchantability() / 4 + 1;
        int cost = settings.baseLevel() + 1 + random.nextInt(range) + random.nextInt(range);
        float deviation = (random.nextFloat() + random.nextFloat() - 1.0F) * 0.15F;
        cost = Math.max(1, Math.round(cost + cost * deviation));
        List<Candidate> candidates = new ArrayList<>();
        for (var entry : settings.definitions().entrySet()) {
            var enchantment = entry.getValue();
            if (!enchantment.allowedItems().contains(item)) continue;
            for (int level = enchantment.maxLevel(); level > 0; level--) {
                if (enchantment.minCost().at(level) <= cost && enchantment.maxCost().at(level) >= cost) {
                    candidates.add(new Candidate(entry.getKey(), enchantment, level));
                    break;
                }
            }
        }
        if (candidates.isEmpty()) throw new IllegalStateException("No fishing enchantment candidates for " + item);
        List<Enchantment> result = new ArrayList<>();
        Candidate selected = choose(candidates, c -> c.definition().weight(), random);
        result.add(toEnchantment(selected));
        while (random.nextInt(50) <= cost) {
            Candidate previous = selected;
            candidates.removeIf(c -> !compatible(previous, c));
            if (candidates.isEmpty()) break;
            selected = choose(candidates, c -> c.definition().weight(), random);
            result.add(toEnchantment(selected));
            cost /= 2;
        }
        return List.copyOf(result);
    }

    private static boolean compatible(Candidate first, Candidate second) {
        return !first.id().equals(second.id())
                && !first.definition().incompatibleWith().contains(second.id())
                && !second.definition().incompatibleWith().contains(first.id());
    }

    private static Enchantment toEnchantment(Candidate candidate) {
        return new Enchantment(candidate.id(), candidate.definition().name(), candidate.level());
    }

    int bonus(List<Enchantment> enchantments) {
        var settings = definition.enchantments();
        int result = 0;
        for (var enchantment : enchantments) {
            if (!settings.curseIds().contains(enchantment.id()))
                result = Math.addExact(result, Math.multiplyExact(enchantment.level(), settings.goldPerNonCurseLevel()));
            if (enchantment.id().equals("minecraft:mending"))
                result = Math.addExact(result, settings.mendingExtraGold());
        }
        return Math.min(result, settings.maxBonusGold());
    }

    private static <T> T choose(List<T> values, ToIntFunction<T> weight, RandomGenerator random) {
        int total = 0;
        for (T value : values) total = Math.addExact(total, weight.applyAsInt(value));
        int roll = random.nextInt(total);
        for (T value : values) {
            roll -= weight.applyAsInt(value);
            if (roll < 0) return value;
        }
        throw new IllegalStateException("Invalid fishing weights");
    }

    private void validate() {
        zone();
        require(definition.rules().dailyLimit() == 1, "Only one daily catch is supported");
        require(Set.of("atribot_strategy_v1", "weighted_per_user_per_day").contains(definition.rules().biomeSelection()), "Invalid biome selection");
        require(definition.categories().keySet().equals(Set.copyOf(CATEGORIES)), "Invalid fishing categories");
        require(CATEGORIES.stream().mapToInt(this::categoryWeight).sum() > 0, "Empty fishing categories");
        require(!definition.biomes().isEmpty(), "Empty fishing biomes");
        Set<String> biomes = new HashSet<>();
        for (Biome biome : definition.biomes()) {
            require(biome.id() != null && biomes.add(biome.id()) && biome.weight() > 0, "Invalid fishing biome");
            require(biome.name() != null && !biome.name().isBlank(), "Missing biome name");
            for (String category : CATEGORIES) {
                List<Entry> entries = new ArrayList<>(definition.commonLoot().get(category));
                if (category.equals("junk")) entries.addAll(biome.appendJunk());
                if (category.equals("treasure")) entries.addAll(biome.appendTreasure());
                require(!entries.isEmpty(), "Empty fishing loot pool");
                Set<String> ids = new HashSet<>();
                for (Entry entry : entries) {
                    require(entry.id() != null && ids.add(entry.id()) && entry.weight() > 0 && entry.gold() > 0, "Invalid fishing entry");
                    require(entry.name() != null && !entry.name().isBlank() && !entry.items().isEmpty(), "Missing fishing item");
                    for (Item item : entry.items())
                        require(item.id() != null && item.id().startsWith("minecraft:") && item.count() > 0, "Invalid item count or ID");
                    require(entry.durability() == null || definition.durability().containsKey(entry.durability()), "Unknown durability profile");
                    require(entry.enchantment() == null || Set.of("bow", "book", "fishing_rod").contains(entry.enchantment()), "Unknown enchantment item");
                }
            }
        }
        for (Damage damage : definition.durability().values())
            require(damage.maxDurability() > 0 && damage.remainingFractionMin() >= 0
                    && damage.remainingFractionMax() <= 1 && damage.remainingFractionMin() < damage.remainingFractionMax(), "Invalid durability range");
        var ench = definition.enchantments();
        require(ench.baseLevel() > 0 && ench.enchantability() > 0 && ench.goldPerNonCurseLevel() >= 0
                && ench.mendingExtraGold() >= 0 && ench.maxBonusGold() >= 0, "Invalid enchantment settings");
        for (var d : ench.definitions().values())
            require(d.name() != null && !d.name().isBlank() && d.weight() > 0 && d.maxLevel() > 0
                    && d.minCost() != null && d.maxCost() != null && !d.allowedItems().isEmpty()
                    && d.incompatibleWith() != null, "Invalid enchantment definition");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
