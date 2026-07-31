package top.yzljc.atribot.function.general.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.service.request.HttpService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽卡系统的可调用函数：消耗金粒抽卡 / 免费抽卡 / 渲染持有总览图。
 * 不做 onCommand 接入，指令层由调用方自行编写；免费抽卡的"每日一次"频率控制同样由调用方负责。
 *
 * @Author YZ_Ljc_
 * @ClassName LootService
 * @Created_at 2026/07/31
 * @Project AtriBot
 * @Package top.yzljc.atribot.function.general.impl
 */
@Slf4j
public class LootService {

    private static final long CATALOG_TTL_MS = 5 * 60_000;

    private static volatile List<LootCatalogItem> catalogCache = List.of();
    private static volatile long catalogFetchedAt = 0;

    public record LootCatalogItem(String itemId, String displayName, String resourceWay, String hash, long createTimestamp) {
    }

    public record DrawResult(boolean success, String message, String itemId, String displayName,
                             String imageUrl, int remainingCoins) {
        public static DrawResult fail(String message) {
            return new DrawResult(false, message, null, null, null, 0);
        }
    }

    /**
     * 获取抽卡目录（本地缓存 5 分钟），失败时返回上一次成功的缓存
     */
    public static synchronized List<LootCatalogItem> getCatalog(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && !catalogCache.isEmpty() && now - catalogFetchedAt < CATALOG_TTL_MS) {
            return catalogCache;
        }

        try {
            JsonNode resp = HttpService.sendGetRequest(ResourcesProperties.LOOTS_API,
                    "Authorization", "Bearer " + Config.getInstance().getAtribotKeySecret());
            if (resp == null || resp.path("status").asInt() != 200) {
                log.warn("获取抽卡目录失败: resp={}", resp);
                return catalogCache;
            }

            List<LootCatalogItem> parsed = new ArrayList<>();
            for (JsonNode node : resp.path("data").path("loots")) {
                parsed.add(new LootCatalogItem(
                        node.path("item_id").asText(),
                        node.path("display_name").asText(),
                        node.path("resource_way").asText(),
                        node.hasNonNull("hash") ? node.path("hash").asText() : null,
                        node.path("create_timestamp").asLong(0)
                ));
            }
            catalogCache = parsed;
            catalogFetchedAt = now;
        } catch (Exception e) {
            log.error("获取抽卡目录异常", e);
        }
        return catalogCache;
    }

    /**
     * 消耗金粒抽卡；金粒不足直接返回失败
     */
    public static DrawResult drawPaid(String userId, int cost) {
        if (cost <= 0) {
            return DrawResult.fail("消耗数量必须大于 0");
        }
        if (!LootRepository.removeCoins(userId, cost)) {
            return DrawResult.fail("金粒不足");
        }
        return performDraw(userId, "抽奖-消耗金粒");
    }

    /**
     * 不消耗金粒的抽卡调用；是否满足"今日免费机会"由调用方自行判断后再调用本方法
     */
    public static DrawResult drawFree(String userId) {
        return performDraw(userId, "抽奖-每日免费");
    }

    private static DrawResult performDraw(String userId, String way) {
        List<LootCatalogItem> catalog = getCatalog(false);
        if (catalog.isEmpty()) {
            return DrawResult.fail("卡池暂不可用");
        }

        LootCatalogItem picked = catalog.get(ThreadLocalRandom.current().nextInt(catalog.size()));
        LootRepository.appendLoot(userId, picked.itemId(), picked.displayName(), way);

        ImageDTO card = PreImageGenerate.dump(ResourcesProperties.LOOTS_DRAW_CARD_API, Map.of("item_id", picked.itemId()));
        String imageUrl = (card != null && !card.isError()) ? card.url() : null;

        return new DrawResult(true, "ok", picked.itemId(), picked.displayName(), imageUrl, LootRepository.getCoins(userId));
    }

    /**
     * 渲染用户持有物品卡的总览图（便于以后做"我的卡片"一类指令）
     */
    public static String renderOverviewCard(String userId) {
        List<LootRepository.LootRecord> owned = LootRepository.getLoots(userId);
        List<Map<String, Object>> items = owned.stream()
                .map(r -> Map.<String, Object>of(
                        "item_id", r.itemId(),
                        "display_name", r.displayName(),
                        "receive_timestamp", r.receiveTimestamp()))
                .toList();

        ImageDTO dto = PreImageGenerate.dump(ResourcesProperties.LOOTS_OVERVIEW_CARD_API, Map.of("items", items));
        return (dto != null && !dto.isError()) ? dto.url() : null;
    }
}
