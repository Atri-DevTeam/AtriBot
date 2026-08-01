package top.yzljc.atribot.function.official.loot;

import top.yzljc.atribot.function.general.impl.ImageDTO;

/**
 * @Author YZ_Ljc_
 * @ClassName LootDao
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.loot
 */
public record LootDao(boolean success, String message, ImageDTO image, boolean duplicated, int refundCoins,
                      boolean freeDraw, int costCoins) {

    public static LootDao success(ImageDTO image) {
        return success(image, false, 0);
    }

    public static LootDao success(ImageDTO image, boolean duplicated, int refundCoins) {
        return success(image, duplicated, refundCoins, false, 0);
    }

    public static LootDao success(ImageDTO image, boolean duplicated, int refundCoins, boolean freeDraw, int costCoins) {
        return new LootDao(true, "Success", image, duplicated, refundCoins, freeDraw, costCoins);
    }

    public static LootDao fail(String message) {
        return new LootDao(false, message, null, false, 0, false, 0);
    }
}
