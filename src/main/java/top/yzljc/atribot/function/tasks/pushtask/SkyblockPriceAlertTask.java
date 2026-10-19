package top.yzljc.atribot.function.tasks.pushtask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.database.repo.SkyblockPriceAlertRepository;
import top.yzljc.atribot.function.skyblockprice.SkyblockMarketClient;
import top.yzljc.atribot.function.skyblockprice.SkyblockPriceAlert;
import top.yzljc.atribot.function.skyblockprice.SkyblockPriceAlertService;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;

import java.io.IOException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author YZ_Ljc_
 * @ClassName SkyblockPriceAlertTask
 * @Created_at 2026/10/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks.pushtask
 */
@Slf4j
public final class SkyblockPriceAlertTask extends PushTask implements ScheduledTask {
    public static final String FUNCTION_ID = "skyblock_price_alert";
    public static final SkyblockPriceAlertTask INSTANCE = new SkyblockPriceAlertTask();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SkyblockMarketClient market = new SkyblockMarketClient();
    private final AtomicBoolean running = new AtomicBoolean();

    private SkyblockPriceAlertTask() {
        super(FUNCTION_ID, "SkyBlock 价格提醒", true, false, true);
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan(ScheduleMode.a_quarter);
    }

    @Override
    public Markdown getDescription(Platform platform, String id) {
        return TC.md("**SkyBlock 价格提醒**\n\n在个人面板中选择物品、报价类型和目标价格，达到条件后通过私聊提醒。\n\n"
                + "每 15 分钟检查一次，新行情达到条件时提醒，同一行情不重复发送。规则持续生效，直至手动停用。\n\n"
                + "使用 /dashboard 打开个人面板，进入「价格提醒」配置。");
    }

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) return;
        try {
            Map<SkyblockMarketClient.Item, List<Watch>> watches = new LinkedHashMap<>();
            for (String user : getEnabledUserOpenIds()) {
                if (!eligible(user)) continue;
                try {
                    for (var rule : SkyblockPriceAlertRepository.load(user)) {
                        if (rule.deliveryToken() != null) {
                            if (rule.leaseUntil() < System.currentTimeMillis()) expire(user, rule);
                            continue;
                        }
                        if (!rule.enabled() || rule.nextAttemptAt() > System.currentTimeMillis()) continue;
                        var item = new SkyblockMarketClient.Item(rule.itemId(), rule.key());
                        watches.computeIfAbsent(item, ignored -> new ArrayList<>()).add(new Watch(user, rule));
                    }
                } catch (SQLException e) {
                    log.warn("价格提醒配置读取失败: user={}", user, e);
                }
            }
            var items = new ArrayList<>(watches.keySet());
            for (int start = 0; start < items.size() && !Thread.currentThread().isInterrupted(); start += 20) {
                List<SkyblockMarketClient.Item> batch = items.subList(start, Math.min(start + 20, items.size()));
                try {
                    var quotes = market.quotes(batch);
                    for (var quote : quotes) {
                        var item = new SkyblockMarketClient.Item(quote.path("item").path("id").asText(), quote.path("item").path("key").asText());
                        for (Watch watch : watches.getOrDefault(item, List.of())) {
                            var price = SkyblockPriceAlert.price(quote, watch.rule().metric(), System.currentTimeMillis());
                            if (price != null && price.updatedAt() > watch.rule().lastSnapshot())
                                check(watch, item, price);
                        }
                    }
                } catch (Exception e) {
                    log.warn("价格提醒行情检查失败: items={}, type={}", batch.size(), e.getClass().getSimpleName());
                }
            }
        } finally {
            running.set(false);
        }
    }

    private static boolean eligible(String user) {
        return SkyblockPriceAlertService.available(user) && OfficialUsers.isC2CPushEnabled(user)
                && !PushTaskGlobalSettings.isDisabledForC2C(FUNCTION_ID)
                && OfficialUsers.isFunctionEnabled(user, FUNCTION_ID);
    }

    private void check(Watch watch, SkyblockMarketClient.Item item, SkyblockPriceAlert.Price price) {
        String token = UUID.randomUUID().toString();
        try {
            boolean claimed = SkyblockPriceAlertRepository.update(watch.user(), rules -> {
                for (int i = 0; i < rules.size(); i++) {
                    var rule = rules.get(i);
                    if (!same(rule, watch.rule())) continue;
                    var observed = rule.observe(price, token, System.currentTimeMillis());
                    rules.set(i, observed);
                    return token.equals(observed.deliveryToken());
                }
                return false;
            });
            if (!claimed) return;
            String error = null;
            try {
                if (!eligible(watch.user())) throw new IllegalStateException("推送已关闭");
                var image = market.icon(item);
                if (!eligible(watch.user())) throw new IllegalStateException("推送权限已撤销或推送已关闭");
                boolean active = SkyblockPriceAlertRepository.load(watch.user()).stream().anyMatch(rule ->
                        rule.enabled() && same(rule, watch.rule()) && token.equals(rule.deliveryToken()));
                if (active && C2CChat.sendMessage(watch.user(), render(watch.rule(), price, image, false)) == null)
                    throw new IllegalStateException("平台未返回消息 ID");
            } catch (Exception e) {
                error = "私聊提醒发送失败，请检查主动消息权限";
                log.warn("价格提醒发送失败: user={}, rule={}, type={}", watch.user(), watch.rule().id(), e.getClass().getSimpleName());
            }
            String failure = error;
            SkyblockPriceAlertRepository.update(watch.user(), rules -> {
                for (int i = 0; i < rules.size(); i++) {
                    var rule = rules.get(i);
                    if (!rule.id().equals(watch.rule().id()) || !token.equals(rule.deliveryToken())) continue;
                    rules.set(i, rule.finish(token, price, failure, System.currentTimeMillis()));
                }
                return null;
            });
        } catch (SQLException e) {
            log.warn("价格提醒状态保存失败: user={}, rule={}", watch.user(), watch.rule().id(), e);
        }
    }

    /**
     * 向管理员本人发送测试消息，不改变提醒的开关、完成状态和行情检查记录
     *
     * @param userId       已验证的官机用户标识
     * @param rule         该用户拥有的提醒配置
     * @param checkSession 发送前的会话有效性检查
     */
    public void sendTest(String userId, SkyblockPriceAlert rule, Runnable checkSession) {
        SkyblockPriceAlertService.requireTestPermission(userId);
        SkyblockPriceAlertService.requireAvailable(userId);
        if (!OfficialUsers.isC2CPushEnabled(userId))
            throw new SkyblockPriceAlertService.Problem(409, "PRICE_ALERT_PUSH_DISABLED");
        var item = new SkyblockMarketClient.Item(rule.itemId(), rule.key());
        SkyblockPriceAlert.Price price = null;
        JsonNode image = null;
        try {
            price = SkyblockPriceAlert.price(market.quote(item), rule.metric(), System.currentTimeMillis());
        } catch (IOException ignored) {
        }
        try {
            image = market.icon(item);
        } catch (IOException ignored) {
        }
        checkSession.run();
        SkyblockPriceAlertService.requireTestPermission(userId);
        SkyblockPriceAlertService.requireAvailable(userId);
        if (!OfficialUsers.isC2CPushEnabled(userId))
            throw new SkyblockPriceAlertService.Problem(409, "PRICE_ALERT_PUSH_DISABLED");
        try {
            if (C2CChat.sendMessage(userId, render(rule, price, image, true)) == null)
                throw new IllegalStateException("平台未返回消息 ID");
        } catch (Exception e) {
            log.warn("价格提醒测试发送失败: user={}, rule={}, type={}", userId, rule.id(), e.getClass().getSimpleName());
            throw new SkyblockPriceAlertService.Problem(502, "PRICE_ALERT_TEST_FAILED");
        }
    }

    static Markdown render(SkyblockPriceAlert rule, SkyblockPriceAlert.Price price, JsonNode image, boolean test) {
        String variant = variant(rule.variant());
        StringBuilder text = new StringBuilder("**SkyBlock Item Price Alert**\n\n");
        if (image != null) {
            String url = image.path("url").asText(null);
            if (url == null && image.path("hash").asText().matches("[0-9a-f]{64}"))
                url = OpenApi.get("bot.hypixel.skyblock.icon.get", "hash", image.path("hash").asText());
            if (url != null) text.append(Markdown.img(url, 32, 32)).append("\n\n");
        }
        text.append(safe(rule.name())).append("\n\n");
        if (!variant.isBlank()) text.append("> 变体：").append(safe(variant)).append('\n');
        text.append("> 来源：").append(SkyblockPriceAlert.METRICS.get(rule.metric())).append('\n');
        String current = "暂无信息";
        if (price != null) {
            var numberFormat = NumberFormat.getNumberInstance(Locale.US);
            numberFormat.setMaximumFractionDigits(Math.max(0, price.amount().stripTrailingZeros().scale()));
            current = "**" + numberFormat.format(price.amount()) + " Coins**";
        }
        text.append("> 当前：").append(current).append('\n');
//        text.append("> 条件：").append(rule.direction().equals("below") ? "≤ " : "≥ ")
//                .append(rule.threshold().stripTrailingZeros().toPlainString()).append(" Coins\n");
        if (price != null)
            text.append("> 更新时间：").append(TIME.format(Instant.ofEpochMilli(price.updatedAt()))).append('\n');
        text.append(test ? "\n测试消息，无法更改状态" : "\n" + Markdown.enterCommand("/preferences skyblock_price disable " + rule.id(), "停用当前规则"));
        return TC.md(text.toString());
    }

    private static void expire(String user, SkyblockPriceAlert previous) throws SQLException {
        SkyblockPriceAlertRepository.update(user, rules -> {
            for (int i = 0; i < rules.size(); i++) {
                var rule = rules.get(i);
                if (same(rule, previous)) rules.set(i, rule.expireDelivery(System.currentTimeMillis()));
            }
            return null;
        });
    }

    private static boolean same(SkyblockPriceAlert a, SkyblockPriceAlert b) {
        return a.id().equals(b.id()) && a.revision().equals(b.revision());
    }

    private static String safe(String text) {
        return text.replaceAll("[\\r\\n\\[\\]<>*_`#]", "");
    }

    private static String variant(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            var data = JSON.readTree(value);
            if (data.hasNonNull("pet_type")) {
                return data.path("pet_type").asText() + " · " + data.path("pet_tier").asText()
                        + " · " + data.path("pet_skin").asText("默认皮肤") + " · 跨等级最低价";
            }
            var enchantments = data.path("enchantments");
            var labels = new ArrayList<String>();
            if (enchantments.isObject())
                enchantments.properties().forEach(entry -> labels.add(entry.getKey() + " " + entry.getValue().asInt()));
            return String.join(" · ", labels);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private record Watch(String user, SkyblockPriceAlert rule) {
    }
}
