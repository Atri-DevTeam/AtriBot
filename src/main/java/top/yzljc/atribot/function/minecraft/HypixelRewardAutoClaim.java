package top.yzljc.atribot.function.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.function.command.HypixelRewardCommand;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel 奖励自动领取机制层（仅官机平台，Napcat 仍走手动选择）。
 * <p>
 * 用户偏好存储在 OfficialUsers 的 user_settings 下，根键为 {@link #SETTING_KEY}：
 * <pre>hypixel_reward: {"enabled": true, "first_claim": "rarity|priority", "item_priority": {"BEDWARS": 100, ...}}</pre>
 * 其中 item_priority 的键使用 {@link HypixelRewardCommand#itemNamespace} 中代表游戏模式的规范键（如 BEDWARS、ARCADE），
 * 未设置的物品默认优先级为 0。
 * <p>
 * 奖励内容中出现 itemNamespace 未收录的新物品时（提取不出规范键），该物品按优先级 0
 * 参与自动选择，并通过 {@link Alert} 上报开发者（同一物品仅上报一次）。
 *
 * @Author YZ_Ljc_
 * @ClassName HypixelRewardAutoClaim
 * @Project AtriBot
 * @Package top.yzljc.atribot.function.command
 */
public class HypixelRewardAutoClaim {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 用户偏好设置在 user_settings 中的根键 */
    public static final String SETTING_KEY = "hypixel_reward";
    /** first_claim 取值：稀有度优先 */
    public static final String MODE_RARITY = "rarity";
    /** first_claim 取值：奖励内容优先级优先 */
    public static final String MODE_PRIORITY = "priority";

    private static final Pattern QUANTITY_PATTERN = Pattern.compile("\\d[\\d,]*");

    private static final Map<String, Integer> RARITY_RANK = Map.of(
            "COMMON", 0,
            "RARE", 1,
            "EPIC", 2,
            "LEGENDARY", 3
    );

    /** 物品规范键索引：key 为 itemNamespace 键的小写形式，value 为规范键本身 */
    private static final Map<String, String> CANONICAL_ITEM_KEYS;

    /** 仅用于展示翻译、不能代表具体游戏模式的泛用奖励键。 */
    public static final Set<String> GENERIC_REWARD_KEYS = Set.of("coins", "tokens");

    /** 已上报过的未知物品签名，避免同一新物品反复上报刷屏 */
    private static final Set<String> reportedUnknownItems = ConcurrentHashMap.newKeySet();

    static {
        Map<String, String> index = new LinkedHashMap<>();
        for (String key : HypixelRewardCommand.itemNamespace.keySet()) {
            if (GENERIC_REWARD_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            index.put(key.toLowerCase(Locale.ROOT), key);
        }
        CANONICAL_ITEM_KEYS = Collections.unmodifiableMap(index);
    }

    public record Settings(boolean enabled, String firstClaim, Map<String, Integer> itemPriority) {

        public static Settings defaults() {
            return new Settings(false, MODE_RARITY, Map.of());
        }

        /** 查询某物品的用户优先级，未设置返回 0 */
        public int priorityOf(String itemKey) {
            if (itemKey == null) {
                return 0;
            }
            return itemPriority.getOrDefault(itemKey, 0);
        }
    }

    /**
     * 读取用户的自动领取偏好设置，未配置时返回默认值（关闭、稀有度优先、全部未设置）
     */
    public static Settings getSettings(String userOpenId) {
        JsonNode node = OfficialUsers.getUserSetting(userOpenId, SETTING_KEY);
        if (node == null || !node.isObject()) {
            return Settings.defaults();
        }

        JsonNode enabledNode = node.path("enabled");
        boolean enabled = enabledNode.isBoolean() && enabledNode.asBoolean();

        JsonNode firstClaimNode = node.path("first_claim");
        String firstClaim = firstClaimNode.isTextual() ? firstClaimNode.asText() : MODE_RARITY;

        Map<String, Integer> priorities = new LinkedHashMap<>();
        JsonNode priorityNode = node.path("item_priority");
        if (priorityNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = priorityNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue().isInt()) {
                    priorities.put(e.getKey(), e.getValue().asInt());
                }
            }
        }
        return new Settings(enabled, firstClaim, Collections.unmodifiableMap(priorities));
    }

    public static boolean isAutoClaimEnabled(String userOpenId) {
        return getSettings(userOpenId).enabled();
    }

    public static boolean setEnabled(String userOpenId, boolean enabled) {
        return updateSettings(userOpenId, root -> root.put("enabled", enabled));
    }

    /** 设置优先判定模式，mode 只能是 MODE_RARITY / MODE_PRIORITY */
    public static boolean setFirstClaim(String userOpenId, String mode) {
        if (!MODE_RARITY.equals(mode) && !MODE_PRIORITY.equals(mode)) {
            return false;
        }
        return updateSettings(userOpenId, root -> root.put("first_claim", mode));
    }

    /**
     * 设置某物品的优先级（1-30），itemKey 须为 {@link #knownItemKeys()} 中的规范键。
     * 已存在的旧优先级值不会被迁移或修改，仍可正常参与选择。
     */
    public static boolean setItemPriority(String userOpenId, String itemKey, int priority) {
        String canonicalKey = canonicalItemKey(itemKey);
        if (canonicalKey == null || priority < 1 || priority > 30) {
            return false;
        }
        return updateSettings(userOpenId, root -> {
            JsonNode node = root.get("item_priority");
            ObjectNode priorities = (node != null && node.isObject()) ? (ObjectNode) node : root.putObject("item_priority");
            priorities.put(canonicalKey, priority);
        });
    }

    /** 清除某物品的优先级（回到未设置状态，视为 0） */
    public static boolean removeItemPriority(String userOpenId, String itemKey) {
        String canonicalKey = canonicalItemKey(itemKey);
        if (canonicalKey == null) {
            return false;
        }
        return updateSettings(userOpenId, root -> {
            JsonNode node = root.get("item_priority");
            if (node != null && node.isObject()) {
                ((ObjectNode) node).remove(canonicalKey);
            }
        });
    }

    private static String canonicalItemKey(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return CANONICAL_ITEM_KEYS.values().stream()
                .filter(key -> key.equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);
    }

    private static boolean updateSettings(String userOpenId, Consumer<ObjectNode> mutator) {
        JsonNode node = OfficialUsers.getUserSetting(userOpenId, SETTING_KEY);
        ObjectNode root = (node != null && node.isObject()) ? (ObjectNode) node : mapper.createObjectNode();
        mutator.accept(root);
        return OfficialUsers.setUserSetting(userOpenId, SETTING_KEY, root);
    }

    /**
     * 一个可领取的奖励选项
     *
     * @param index    在 rewards 列表中的下标（即 claim 的 choice）
     * @param rarity   稀有度（COMMON/RARE/EPIC/LEGENDARY，解析失败按 COMMON）
     * @param itemKey  从内容中提取的物品规范键，无命中为 null
     * @param quantity 内容中的第一个数字（数量），无数字为 null
     */
    public record RewardOption(int index, String rarity, String itemKey, Long quantity) {
    }

    public static List<RewardOption> parseOptions(List<String> rewardLines) {
        List<RewardOption> options = new ArrayList<>();
        if (rewardLines == null) {
            return options;
        }
        for (int i = 0; i < rewardLines.size(); i++) {
            String line = rewardLines.get(i);
            String rarity = "COMMON";
            String content = line == null ? "" : line;

            Matcher lineMatcher = HypixelRewardCommand.REWARD_LINE_PATTERN.matcher(content);
            if (lineMatcher.matches()) {
                rarity = lineMatcher.group(2).toUpperCase(Locale.ROOT);
                content = lineMatcher.group(3);
            }

            String itemKey = extractItemKey(content);
            if (itemKey == null && content != null && !content.isBlank()) {
                // 提取不出规范键说明出现了 itemNamespace 未收录的新物品
                alertUnknownItem(line);
            }
            options.add(new RewardOption(i, rarity, itemKey, extractQuantity(content)));
        }
        return options;
    }

    /**
     * 上报未知的新奖励物品。签名去掉数字（同一物品的数量每次会变），只按文本部分去重。
     */
    private static void alertUnknownItem(String rewardLine) {
        String signature = rewardLine.replaceAll("[\\d,]", "").trim();
        if (reportedUnknownItems.add(signature)) {
            Alert.notify("检测到未知的 Hypixel 奖励物品，已按优先级 0 参与自动领取：" + rewardLine
                    + " ，请及时补充 itemNamespace 喵！");
        }
    }

    /** 从奖励内容中提取第一个命中 itemNamespace 的词，返回其规范键，无命中返回 null */
    public static String extractItemKey(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher keyMatcher = HypixelRewardCommand.REWARD_KEY_PATTERN.matcher(content);
        while (keyMatcher.find()) {
            String canonical = CANONICAL_ITEM_KEYS.get(keyMatcher.group().toLowerCase(Locale.ROOT));
            if (canonical != null) {
                return canonical;
            }
        }
        return null;
    }

    /** 提取内容中的第一个数字作为数量（兼容千分位逗号），无数字返回 null */
    private static Long extractQuantity(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = QUANTITY_PATTERN.matcher(content);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group().replace(",", ""));
            } catch (NumberFormatException ignored) {
                // 数字过长等异常情况，视为无数量
            }
        }
        return null;
    }

    /** 可供设置优先级的全部物品规范键 */
    public static Set<String> knownItemKeys() {
        return Set.copyOf(CANONICAL_ITEM_KEYS.values());
    }

    // ==================== 自动选择 ====================

    /**
     * 自动领取入口：用户开启自动模式时，从奖励行文本中按其偏好选出应领取的选项。
     * 未开启自动模式、奖励列表为空时返回 null（调用方回退到手动选择）。
     */
    public static Integer selectReward(String userOpenId, List<String> rewardLines) {
        Settings settings = getSettings(userOpenId);
        if (!settings.enabled()) {
            return null;
        }
        List<RewardOption> options = parseOptions(rewardLines);
        if (options.isEmpty()) {
            return null;
        }
        return select(settings, options);
    }

    /**
     * 选择算法核心（纯函数，不依赖数据库）。
     * <ul>
     *   <li>first_claim = rarity：先取稀有度最高者；first_claim = priority：先取用户优先级最高者</li>
     *   <li>候选稀有度全部相同时两种模式等价，此时直接由优先级决定</li>
     *   <li>同优先级时：数量多者优先，其次稀有度高者；候选中存在无数量的则整体随机</li>
     * </ul>
     */
    public static Integer select(Settings settings, List<RewardOption> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }

        boolean rarityFirst = !MODE_PRIORITY.equals(settings.firstClaim());
        List<RewardOption> group = new ArrayList<>(options);

        if (rarityFirst) {
            group = filterMax(group, HypixelRewardAutoClaim::rarityRank);
            group = filterMax(group, option -> settings.priorityOf(option.itemKey()));
        } else {
            group = filterMax(group, option -> settings.priorityOf(option.itemKey()));
        }

        // 同优先级比数量、稀有度；只要候选中有缺数量的，按约定直接随机（保留当前候选组）
        if (!group.isEmpty() && group.stream().allMatch(o -> o.quantity() != null)) {
            group = filterMax(group, RewardOption::quantity);
            group = filterMax(group, HypixelRewardAutoClaim::rarityRank);
        }

        if (group.isEmpty()) {
            return null;
        }
        return group.get(ThreadLocalRandom.current().nextInt(group.size())).index();
    }

    private static int rarityRank(RewardOption option) {
        return RARITY_RANK.getOrDefault(option.rarity(), 0);
    }

    /** 保留 key 值最大的元素，其余丢弃 */
    private static List<RewardOption> filterMax(List<RewardOption> options, ToLongFunction<RewardOption> key) {
        long max = Long.MIN_VALUE;
        for (RewardOption option : options) {
            max = Math.max(max, key.applyAsLong(option));
        }
        List<RewardOption> result = new ArrayList<>();
        for (RewardOption option : options) {
            if (key.applyAsLong(option) == max) {
                result.add(option);
            }
        }
        return result;
    }
}
