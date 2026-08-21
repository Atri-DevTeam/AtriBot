package top.yzljc.atribot.function.official.loot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.request.HttpService;

import java.io.File;
import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName LootService
 * @Created_at 2026/07/31
 * @Project AtriBot
 * @Package top.yzljc.atribot.function.general.impl
 */
@Slf4j
public class LootService {

    private static final long CATALOG_TTL_MS = 5 * 60_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime FREE_DRAW_LOCK_START = LocalTime.of(23, 50);
    private static final File FREE_DRAW_RECORD_FILE = new File(Properties.LOOT_FREE_DRAW_RECORD);
    private static final int OWNED_ITEM_WEIGHT = 40;
    private static final int UNOWNED_ITEM_WEIGHT = 60;

    private static volatile List<LootCatalogItem> catalogCache = List.of();
    private static volatile long catalogFetchedAt = 0;

    public record LootCatalogItem(String itemId, String displayName, String resourceWay, String hash, long createTimestamp,
                                  boolean special) {
    }

    public record DrawResult(boolean success, String message, String itemId, String displayName,
                             String imageUrl, int remainingCoins) {
        public static DrawResult fail(String message) {
            return new DrawResult(false, message, null, null, null, 0);
        }
    }

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
                        node.path("create_timestamp").asLong(0),
                        node.path("special").asBoolean(false)
                ));
            }
            Collections.shuffle(parsed, RANDOM);
            catalogCache = parsed;
            catalogFetchedAt = now;
        } catch (Exception e) {
            log.error("获取抽卡目录异常", e);
        }
        return catalogCache;
    }

    private enum FreeDrawClaimStatus {
        CLAIMED,
        USED,
        LOCKED,
        ERROR
    }

    private record FreeDrawClaimResult(FreeDrawClaimStatus status, String message) {
    }

    public static LootDao drawPaid(String userId, int cost) {
        if (cost <= 0) {
            return LootDao.fail("消耗数量必须大于 0 - 开发错误，请联系开发者处理");
        }
        if (!LootRepository.removeCoins(userId, cost)) {
            return LootDao.fail("金粒不足，先去获得更多金粒再来尝试吧！");
        }
        return performDraw(userId, "抽奖-消耗金粒", true, false, cost);
    }

    public static LootDao drawDailyFreeOrPaid(String userId, int paidCost) {
        FreeDrawClaimResult claim = claimDailyFreeDraw(userId);
        if (claim.status() == FreeDrawClaimStatus.CLAIMED) {
            LootDao result = performDraw(userId, "抽奖-每日免费", false, true, 0);
            if (!result.success()) {
                rollbackDailyFreeDraw(userId);
            }
            return result;
        }
        if (claim.status() == FreeDrawClaimStatus.USED || claim.status() == FreeDrawClaimStatus.LOCKED) {
            return drawPaid(userId, paidCost);
        }
        return LootDao.fail(claim.message());
    }

    /**
     * 不消耗金粒的抽卡调用；是否满足"今日免费机会"由调用方自行判断后再调用本方法
     */
    public static LootDao drawFree(String userId) {
        FreeDrawClaimResult claim = claimDailyFreeDraw(userId);
        if (claim.status() != FreeDrawClaimStatus.CLAIMED) {
            return LootDao.fail(claim.message());
        }

        LootDao result = performDraw(userId, "抽奖-每日免费", false, true, 0);
        if (!result.success()) {
            rollbackDailyFreeDraw(userId);
        }
        return result;
    }

    private static synchronized FreeDrawClaimResult claimDailyFreeDraw(String userId) {
        if (userId == null || userId.isBlank()) {
            return new FreeDrawClaimResult(FreeDrawClaimStatus.ERROR, "用户信息异常，无法使用免费抽卡");
        }
        if (isFreeDrawLockedNow()) {
            return new FreeDrawClaimResult(FreeDrawClaimStatus.LOCKED, "每日免费抽卡正在结算中，23:50 至 00:00 暂不可用");
        }

        ObjectNode root = loadTodayFreeDrawRecord();
        ObjectNode users = ensureUsersNode(root);
        if (users.has(userId)) {
            return new FreeDrawClaimResult(FreeDrawClaimStatus.USED, "今天的免费抽卡机会已经用过了，明天再来吧");
        }
        users.put(userId, System.currentTimeMillis());
        if (!saveFreeDrawRecord(root)) {
            return new FreeDrawClaimResult(FreeDrawClaimStatus.ERROR, "免费抽卡记录保存失败，请稍后再试");
        }
        return new FreeDrawClaimResult(FreeDrawClaimStatus.CLAIMED, "ok");
    }

    private static synchronized void rollbackDailyFreeDraw(String userId) {
        ObjectNode root = loadTodayFreeDrawRecord();
        ObjectNode users = ensureUsersNode(root);
        users.remove(userId);
        saveFreeDrawRecord(root);
    }

    public static synchronized void clearDailyFreeDrawRecord() {
        try {
            if (FREE_DRAW_RECORD_FILE.exists() && !FREE_DRAW_RECORD_FILE.delete()) {
                log.warn("删除免费抽卡记录失败: {}", FREE_DRAW_RECORD_FILE.getPath());
            }
        } catch (Exception e) {
            log.error("删除免费抽卡记录异常", e);
        }
    }

    private static boolean isFreeDrawLockedNow() {
        LocalTime now = LocalTime.now(BEIJING_ZONE);
        return !now.isBefore(FREE_DRAW_LOCK_START);
    }

    private static ObjectNode loadTodayFreeDrawRecord() {
        String today = LocalDate.now(BEIJING_ZONE).toString();
        try {
            if (FREE_DRAW_RECORD_FILE.exists()) {
                JsonNode root = MAPPER.readTree(FREE_DRAW_RECORD_FILE);
                if (root instanceof ObjectNode objectNode && today.equals(objectNode.path("date").asText())) {
                    ensureUsersNode(objectNode);
                    return objectNode;
                }
            }
        } catch (Exception e) {
            log.warn("读取免费抽卡记录失败，将重建记录: {}", FREE_DRAW_RECORD_FILE.getPath(), e);
        }

        ObjectNode root = MAPPER.createObjectNode();
        root.put("date", today);
        root.set("users", MAPPER.createObjectNode());
        return root;
    }

    private static ObjectNode ensureUsersNode(ObjectNode root) {
        JsonNode users = root.get("users");
        if (users instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode objectNode = MAPPER.createObjectNode();
        root.set("users", objectNode);
        return objectNode;
    }

    private static boolean saveFreeDrawRecord(ObjectNode root) {
        try {
            File parent = FREE_DRAW_RECORD_FILE.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("创建免费抽卡记录目录失败: {}", parent.getPath());
                return false;
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(FREE_DRAW_RECORD_FILE, root);
            return true;
        } catch (Exception e) {
            log.error("保存免费抽卡记录失败", e);
            return false;
        }
    }

    private static LootDao performDraw(String userId, String way, boolean refundDuplicated, boolean freeDraw, int costCoins) {
        List<LootCatalogItem> catalog = getCatalog(false);
        if (catalog.isEmpty()) {
            return LootDao.fail("卡池暂不可用 - 开发错误，请联系开发者处理");
        }

        // special 卡仅作为特殊奖励发放，任何用户都抽不到
        List<LootCatalogItem> drawable = catalog.stream().filter(item -> !item.special()).toList();
        if (drawable.isEmpty()) {
            return LootDao.fail("卡池暂不可用 - 开发错误，请联系开发者处理");
        }

        Set<String> drawableItemIds = new HashSet<>();
        for (LootCatalogItem item : drawable) {
            drawableItemIds.add(item.itemId());
        }

        List<LootRepository.LootRecord> ownedLoots = LootRepository.getLoots(userId);
        Set<String> ownedItemIds = new HashSet<>();
        for (LootRepository.LootRecord loot : ownedLoots) {
            if (drawableItemIds.contains(loot.itemId())) {
                ownedItemIds.add(loot.itemId());
            }
        }

        LootCatalogItem picked = pickByOwnedWeight(drawable, ownedItemIds);
        boolean duplicated = ownedItemIds.contains(picked.itemId());
        LootRepository.LootRecord record = LootRepository.appendLoot(userId, picked.itemId(), picked.displayName(), way, picked.special());

        int refundCoins = 0;
        if (duplicated && refundDuplicated) {
            refundCoins = RANDOM.nextInt(16) + 5;
            LootRepository.addCoins(userId, refundCoins);
        }

        ImageDTO card = requestDrawCard(picked.itemId());
        if (card.isError() || card.url() == null) {
            return LootDao.fail("渲染抽卡图失败 - 开发错误，请联系开发者处理");
        }

        int currentCount = record != null ? record.count() : 0;
        boolean currentSpecial = record != null && record.special();
        return LootDao.success(card, duplicated, refundCoins, freeDraw, costCoins,
                picked.itemId(), currentCount, currentSpecial);
    }

    private static LootCatalogItem pickByOwnedWeight(List<LootCatalogItem> drawable, Set<String> ownedItemIds) {
        int totalWeight = 0;
        for (LootCatalogItem item : drawable) {
            totalWeight += ownedItemIds.contains(item.itemId()) ? OWNED_ITEM_WEIGHT : UNOWNED_ITEM_WEIGHT;
        }

        int roll = RANDOM.nextInt(totalWeight);
        int cursor = 0;
        for (LootCatalogItem item : drawable) {
            cursor += ownedItemIds.contains(item.itemId()) ? OWNED_ITEM_WEIGHT : UNOWNED_ITEM_WEIGHT;
            if (roll < cursor) {
                return item;
            }
        }
        return drawable.get(RANDOM.nextInt(drawable.size()));
    }

    private static ImageDTO requestDrawCard(String itemId) {
        JsonNode response = HttpService.postJson(ResourcesProperties.LOOTS_DRAW_CARD_API,
                Map.of("item_id", itemId), "Authorization", "Bearer " + Config.getInstance().getAtribotKeySecret());
        if (response == null || response.path("status").asInt() != 200) {
            return new ImageDTO(null, 0, 0, "访问远程数据失败", null);
        }

        JsonNode data = response.path("data");
        String url = data.path("url").asText(null);
        if (url == null || url.isBlank()) {
            String uuid = data.path("uuid").asText(null);
            if (uuid == null || uuid.isBlank()) {
                return new ImageDTO(null, 0, 0, "远程抽卡图响应缺少图片地址", null);
            }
            url = ResourcesProperties.LOOTS_DRAW_CARD_API + "/" + uuid;
        } else if (url.startsWith("/")) {
            String origin = URI.create(ResourcesProperties.LOOTS_DRAW_CARD_API).resolve("/").toString();
            url = URI.create(origin).resolve(url).toString();
        }

        return new ImageDTO(url, data.path("width").asInt(), data.path("height").asInt());
    }

    /**
     * 渲染用户持有物品卡的总览图
     */
    public static LootDao renderOverviewCard(String userId) {
        List<LootRepository.LootRecord> owned = LootRepository.getLoots(userId);
        List<Map<String, Object>> items = owned.stream()
                .map(r -> Map.<String, Object>of(
                        "item_id", r.itemId(),
                        "display_name", r.displayName(),
                        "receive_timestamp", r.receiveTimestamp(),
                        "count", r.count(),
                        "special", r.special()))
                .toList();

        ImageDTO dto = PreImageGenerate.dump(ResourcesProperties.LOOTS_OVERVIEW_CARD_API,
                Map.of("items", items, "user_id", userId));
        if (dto.isError() || dto.url() == null) {
            return LootDao.fail("渲染总览图失败 - 开发错误，请联系开发者处理");
        }
        return LootDao.success(dto);
    }
}
