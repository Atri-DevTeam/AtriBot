package top.yzljc.atribot.function.skyblockprice;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName SkyblockPriceAlert
 * @Created_at 2026/10/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.skyblockprice
 */
public record SkyblockPriceAlert(String id, String revision, String itemId, String key, String name,
                                String variant, String metric, String direction, BigDecimal threshold,
                                boolean enabled, long createdAt, long completedAt, long lastSnapshot,
                                BigDecimal lastPrice, String deliveryToken, long leaseUntil,
                                long nextAttemptAt, String error) {
    public static final Map<String, String> METRICS = Map.of(
            "instant_buy", "Instant Buy", "instant_sell", "Instant Sell", "buy_order", "Buy Order",
            "sell_offer", "Sell Offer", "lowest_bin", "Lowest Bin");

    /**
     * 只读取新鲜且可用的对应市场报价，不将缺失值转为零
     *
     * @param quote 指定物品及来源状态
     * @param metric 报价类型
     * @param now 当前毫秒时间戳
     * @return 可用报价，缺失、过期或非法数据返回 null
     */
    public static Price price(JsonNode quote, String metric, long now) {
        if (quote == null || !METRICS.containsKey(metric)) return null;
        String market = metric.equals("lowest_bin") ? "auction" : "bazaar";
        JsonNode source = quote.path("sources").path(market.equals("auction") ? "auctions" : market);
        long updated = source.path("last_updated").asLong(0);
        long ageLimit = metric.equals("lowest_bin") ? 1_200_000 : 300_000;
        if (!source.path("available").asBoolean(false) || source.path("stale").asBoolean(true)
                || updated <= 0 || updated > now + 60_000 || now - updated > ageLimit) return null;
        JsonNode value = quote.path("item").path(market).path(metric);
        if (!value.isNumber()) return null;
        BigDecimal amount;
        try { amount = value.decimalValue(); }
        catch (NumberFormatException e) { return null; }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000000000000")) > 0) return null;
        return new Price(amount, updated);
    }

    public boolean matches(BigDecimal amount) {
        return amount != null && (direction.equals("below") ? amount.compareTo(threshold) <= 0
                : direction.equals("above") && amount.compareTo(threshold) >= 0);
    }

    /**
     * 对同一行情快照只检查一次，达到阈值时占用发送权，等待发送结果确认
     *
     * @param price 已通过来源有效性检查的报价
     * @param token 本次发送标识
     * @param now 当前毫秒时间戳
     * @return 更新后的状态；不可执行时返回当前实例
     */
    public SkyblockPriceAlert observe(Price price, String token, long now) {
        if (!enabled || deliveryToken != null || price.updatedAt() <= lastSnapshot || now < nextAttemptAt)
            return this;
        boolean reached = matches(price.amount());
        return state(true, 0, reached ? lastSnapshot : price.updatedAt(), price.amount(), reached ? token : null,
                reached ? now + 600_000 : 0, 0, null);
    }

    /**
     * @param token 占用发送权时生成的标识，只有当前发送者可以确认结果
     * @param price 触发本次发送的报价
     * @param failure 失败提示，null 表示平台已返回消息 ID
     * @param now 当前毫秒时间戳
     * @return 成功后记录行情快照、失败后延迟重试的状态；保留当前开关，标识不匹配时保持原状态
     */
    public SkyblockPriceAlert finish(String token, Price price, String failure, long now) {
        if (deliveryToken == null || !deliveryToken.equals(token)) return this;
        return state(enabled, 0, failure == null ? price.updatedAt() : lastSnapshot,
                price.amount(), null, 0, failure == null ? 0 : now + 300_000, failure);
    }

    /**
     * @return 停用后的规则；保留在途发送标识，后续发送结果不得重新启用规则
     */
    public SkyblockPriceAlert stop() {
        return new SkyblockPriceAlert(id, UUID.randomUUID().toString(), itemId, key, name, variant,
                metric, direction, threshold, false, createdAt, 0, lastSnapshot, lastPrice, deliveryToken,
                leaseUntil, 0, null);
    }

    /**
     * @param now 当前毫秒时间戳
     * @return 超时未确认的发送转为暂停，不自动重发无法确认结果的消息
     */
    public SkyblockPriceAlert expireDelivery(long now) {
        if (deliveryToken == null || leaseUntil >= now) return this;
        return state(false, 0, lastSnapshot, lastPrice, null, 0, 0,
                "上次发送中断，结果无法确认，请检查私聊记录后重新启用");
    }

    private SkyblockPriceAlert state(boolean enabled, long completed, long snapshot, BigDecimal price,
                                     String token, long lease, long retry, String error) {
        return new SkyblockPriceAlert(id, revision, itemId, key, name, variant, metric, direction, threshold,
                enabled, createdAt, completed, snapshot, price, token, lease, retry, error);
    }

    public record Price(BigDecimal amount, long updatedAt) {}
}
