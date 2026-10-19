package top.yzljc.atribot.function.skyblockprice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.database.repo.SkyblockPriceAlertRepository;
import top.yzljc.atribot.function.skyblockprice.SkyblockMarketClient.Item;
import top.yzljc.atribot.function.tasks.pushtask.SkyblockPriceAlertTask;
import top.yzljc.atribot.function.tasks.pushtask.PushTaskGlobalSettings;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.TC;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName SkyblockPriceAlertService
 * @Created_at 2026/10/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.skyblockprice
 */
public final class SkyblockPriceAlertService {
    public static final String USE_PERMISSION = "bot.skyblock.price";
    public static final SkyblockPriceAlertService INSTANCE = new SkyblockPriceAlertService();
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SkyblockMarketClient market = new SkyblockMarketClient();

    /** 沿用机器人用户权限规则，包括管理员和通配权限；不接受客户端传入的权限声明。 */
    public static boolean canUse(String userId) {
        return userId != null && !userId.isBlank()
                && (OfficialUsers.isAdmin(userId) || OfficialUsers.hasPermission(userId, USE_PERMISSION));
    }

    public static void requirePermission(String userId) {
        if (!canUse(userId)) throw new Problem(403, "PRICE_PERMISSION_REQUIRED");
    }

    public static void requireTestPermission(String userId) {
        if (!OfficialUsers.isAdmin(userId)) throw new Problem(403, "PRICE_ALERT_TEST_FORBIDDEN");
    }

    public static boolean available(String userId) {
        return canUse(userId) && OfficialUsers.isCached(userId) && !OfficialUsers.isBlocked(userId) && !OfficialUsers.isIgnored(userId);
    }

    public static void requireAvailable(String userId) {
        requirePermission(userId);
        if (!available(userId)) throw new Problem(409, "PRICE_ALERT_FRIEND_REQUIRED");
    }

    public Map<String, Object> load(String userId) throws SQLException {
        requirePermission(userId);
        return Map.of("available", available(userId), "enabled", OfficialUsers.isFunctionEnabled(userId, SkyblockPriceAlertTask.FUNCTION_ID),
                "canPush", OfficialUsers.isC2CPushEnabled(userId) && !PushTaskGlobalSettings.isDisabledForC2C(SkyblockPriceAlertTask.FUNCTION_ID),
                "canTest", OfficialUsers.isAdmin(userId), "limit", 20,
                "rules", SkyblockPriceAlertRepository.load(userId).stream().map(rule -> {
                    ObjectNode result = JSON.valueToTree(rule);
                    result.put("sending", rule.deliveryToken() != null);
                    result.remove(List.of("deliveryToken", "leaseUntil"));
                    return result;
                }).toList());
    }

    public JsonNode search(String userId, String query, String cursor) throws IOException {
        requireAvailable(userId);
        if (query == null || query.isBlank() || query.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 128
                || cursor != null && cursor.length() > 1600) throw new Problem(400, "INVALID_PRICE_QUERY");
        return market.search(query, cursor);
    }

    public JsonNode item(String userId, Item item) throws IOException {
        requireAvailable(userId);
        validateItem(item);
        JsonNode result = market.quote(item);
        ((ObjectNode) result.path("item")).remove(List.of("icon", "icon_key"));
        return result;
    }

    public JsonNode icon(String userId, Item item) throws IOException {
        requireAvailable(userId);
        validateItem(item);
        return market.icon(item);
    }

    public void setEnabled(String userId, boolean enabled, Runnable checkSession) throws SQLException {
        requireAvailable(userId);
        if (enabled) {
            if (PushTaskGlobalSettings.isDisabledForC2C(SkyblockPriceAlertTask.FUNCTION_ID))
                throw new Problem(409, "PRICE_ALERT_GLOBALLY_DISABLED");
            if (!OfficialUsers.isC2CPushEnabled(userId)) throw new Problem(409, "PRICE_ALERT_PUSH_DISABLED");
            if (!OfficialUsers.isFunctionEnabled(userId, SkyblockPriceAlertTask.FUNCTION_ID)) {
                String message = C2CChat.sendMessage(userId, TC.md("**SkyBlock 价格提醒已开启**\n\n达到设定价格后，将通过私聊通知。规则持续生效，直至手动停用。"));
                if (message == null) throw new Problem(409, "PRICE_ALERT_PUSH_UNAVAILABLE");
            }
        }
        requireAvailable(userId);
        checkSession.run();
        SkyblockPriceAlertRepository.setEnabled(userId, enabled);
    }

    public record Save(String id, String revision, String itemId, String key, String metric,
                       String direction, BigDecimal threshold, Boolean enabled, String name, String variant) {}
    public record Operation(String action, String id, String revision, Boolean enabled) {}
    public record TestRequest(String id, String revision) {}

    /**
     * @param userId 指令事件确认的官机私聊用户标识
     * @param id 该用户拥有的规则标识，不接受其他用户的规则
     * @throws SQLException 配置读取或保存失败
     */
    public void stop(String userId, String id) throws SQLException {
        requireAvailable(userId);
        if (id == null || id.isBlank() || id.length() > 64) throw new Problem(400, "INVALID_PRICE_ALERT");
        SkyblockPriceAlertRepository.update(userId, rules -> {
            for (int i = 0; i < rules.size(); i++) {
                if (!rules.get(i).id().equals(id)) continue;
                rules.set(i, rules.get(i).stop());
                return null;
            }
            throw new Problem(404, "PRICE_ALERT_NOT_FOUND");
        });
    }

    public void test(String userId, TestRequest request, Runnable checkSession) throws SQLException {
        requireTestPermission(userId);
        requireAvailable(userId);
        if (request == null) throw new Problem(400, "INVALID_PRICE_ALERT");
        var rules = SkyblockPriceAlertRepository.load(userId);
        var rule = rules.get(index(rules, request.id(), request.revision()));
        checkSession.run();
        SkyblockPriceAlertTask.INSTANCE.sendTest(userId, rule, checkSession);
    }

    public void save(String userId, Save request, Runnable checkSession) throws SQLException {
        requireAvailable(userId);
        validateSave(request);
        checkSession.run();
        requireAvailable(userId);
        SkyblockPriceAlertRepository.update(userId, rules -> {
            int index = request.id() == null ? -1 : index(rules, request.id(), request.revision());
            if (index < 0 && rules.size() >= 20) throw new Problem(409, "PRICE_ALERT_LIMIT");
            SkyblockPriceAlert old = index < 0 ? null : rules.get(index);
            if (old != null && old.deliveryToken() != null) throw new Problem(409, "PRICE_ALERT_SENDING");
            var rule = configured(request, old, System.currentTimeMillis());
            if (index < 0) rules.add(rule); else rules.set(index, rule);
            return null;
        });
    }

    public void operate(String userId, Operation request) throws SQLException {
        requireAvailable(userId);
        if (request == null || request.action() == null) throw new Problem(400, "INVALID_PRICE_ALERT");
        SkyblockPriceAlertRepository.update(userId, rules -> {
            int index = index(rules, request.id(), request.revision());
            var old = rules.get(index);
            if (old.deliveryToken() != null) throw new Problem(409, "PRICE_ALERT_SENDING");
            if ("delete".equals(request.action())) rules.remove(index);
            else if ("toggle".equals(request.action()) && request.enabled() != null) {
                rules.set(index, new SkyblockPriceAlert(old.id(), UUID.randomUUID().toString(), old.itemId(), old.key(), old.name(),
                        old.variant(), old.metric(), old.direction(), old.threshold(), request.enabled(), old.createdAt(),
                        request.enabled() ? 0 : old.completedAt(), 0, old.lastPrice(), null, 0, 0, null));
            } else throw new Problem(400, "INVALID_PRICE_ALERT");
            return null;
        });
    }

    static void validateSave(Save request) {
        if (request == null) throw new Problem(400, "INVALID_PRICE_ALERT");
        validateItem(new Item(request.itemId(), request.key()));
        if (!SkyblockPriceAlert.METRICS.containsKey(request.metric() == null ? "" : request.metric())
                || !List.of("below", "above").contains(request.direction() == null ? "" : request.direction())
                || request.threshold() == null || request.threshold().signum() <= 0
                || request.threshold().compareTo(new BigDecimal("1000000000000000")) > 0
                || request.threshold().scale() > 2 || request.enabled() == null)
            throw new Problem(400, "INVALID_PRICE_ALERT");
        if (request.name() != null && request.name().length() > 256)
            throw new Problem(400, "INVALID_PRICE_ALERT");
        if (request.variant() != null && !request.variant().isBlank()) {
            if (request.variant().length() > 4096) throw new Problem(400, "INVALID_PRICE_ALERT");
            try {
                JsonNode variant = JSON.readTree(request.variant());
                if (variant == null || !variant.isObject()) throw new Problem(400, "INVALID_PRICE_ALERT");
            } catch (IOException e) { throw new Problem(400, "INVALID_PRICE_ALERT"); }
        }
    }

    static SkyblockPriceAlert configured(Save request, SkyblockPriceAlert old, long now) {
        boolean sameItem = old != null && old.itemId().equals(request.itemId()) && old.key().equals(request.key());
        String name = request.name() == null || request.name().isBlank()
                ? sameItem ? old.name() : request.itemId() : request.name();
        String variant = request.variant() == null ? sameItem ? old.variant() : "" : request.variant();
        return new SkyblockPriceAlert(old == null ? UUID.randomUUID().toString() : old.id(), UUID.randomUUID().toString(),
                request.itemId(), request.key(), name, variant, request.metric(), request.direction(), request.threshold(),
                Boolean.TRUE.equals(request.enabled()), old == null ? now : old.createdAt(), 0, 0,
                sameItem && old.metric().equals(request.metric()) ? old.lastPrice() : null, null, 0, 0, null);
    }

    private static void validateItem(Item item) {
        if (item == null || item.id() == null || !item.id().matches("[A-Za-z0-9_:.-]{1,128}")
                || item.key() == null || item.key().isBlank() || item.key().length() > 1024)
            throw new Problem(400, "INVALID_PRICE_ITEM");
    }

    private static int index(List<SkyblockPriceAlert> rules, String id, String revision) {
        for (int i = 0; i < rules.size(); i++) {
            var rule = rules.get(i);
            if (rule.id().equals(id)) {
                if (!rule.revision().equals(revision)) throw new Problem(409, "PRICE_ALERT_CHANGED");
                return i;
            }
        }
        throw new Problem(404, "PRICE_ALERT_NOT_FOUND");
    }

    public static final class Problem extends RuntimeException {
        public final int status;
        public final String code;
        public Problem(int status, String code) { super(code); this.status = status; this.code = code; }
    }
}
