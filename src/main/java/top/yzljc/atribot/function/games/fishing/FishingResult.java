package top.yzljc.atribot.function.games.fishing;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName FishingResult
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games.fishing
 */
public record FishingResult(String date, String biomeId, String biomeName, String category,
                            String entryId, String name, List<Item> items, String potion,
                            Durability durability, List<Enchantment> enchantments, int baseGold, int bonusGold,
                            Integer luck) {
    public FishingResult {
        items = List.copyOf(items);
        enchantments = List.copyOf(enchantments);
        if (baseGold < 0 || bonusGold < 0) throw new IllegalArgumentException("Invalid fishing reward");
        if (luck != null && (luck < 0 || luck > 100)) throw new IllegalArgumentException("Invalid fishing luck");
    }

    public int totalGold() {
        return Math.addExact(baseGold, bonusGold);
    }

    public record Item(String id, int count) {
    }

    public record Durability(int remaining, int maximum) {
    }

    public record Enchantment(String id, String name, int level) {
    }
}
