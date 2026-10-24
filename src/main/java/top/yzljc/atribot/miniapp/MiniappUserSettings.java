package top.yzljc.atribot.miniapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.function.minecraft.HypixelRewardAutoClaim;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 个人档案中的用户偏好及设置变更
 *
 * @Author YZ_Ljc_
 * @ClassName MiniappUserSettings
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
public record MiniappUserSettings(String bilibiliUid, Reward reward, Share share, boolean unsupportedKeyboard) {
    public record Priority(String item, int value) {
    }

    public record Reward(boolean enabled, String mode, List<Priority> priorities, List<String> items) {
    }

    public record Share(String url, int invitedCount, boolean invitedBySomeone, String createdAt) {
    }

    public record Change(String action, boolean enabled, String mode, String item, Integer value) {
        /**
         * @param request 仅包含操作类型及对应设置字段的请求
         * @return 通过字段白名单和取值校验的设置变更
         * @throws IllegalArgumentException 字段或设置值无效
         */
        public static Change parse(JsonNode request) {
            if (request == null || !request.isObject() || !request.path("action").isTextual())
                throw new IllegalArgumentException("Invalid settings change");
            String action = request.path("action").textValue();
            Set<String> fields = switch (action) {
                case "reward_enabled", "keyboard" -> Set.of("action", "enabled");
                case "reward_mode" -> Set.of("action", "mode");
                case "reward_priority" -> Set.of("action", "item", "value");
                default -> throw new IllegalArgumentException("Invalid settings action");
            };
            if (request.size() != fields.size()) throw new IllegalArgumentException("Invalid settings fields");
            request.fieldNames().forEachRemaining(field -> {
                if (!fields.contains(field)) throw new IllegalArgumentException("Invalid settings field");
            });
            boolean enabled = false;
            String mode = null, item = null;
            Integer value = null;
            switch (action) {
                case "reward_enabled", "keyboard" -> {
                    if (!request.path("enabled").isBoolean()) throw new IllegalArgumentException("Invalid switch");
                    enabled = request.path("enabled").booleanValue();
                }
                case "reward_mode" -> {
                    mode = request.path("mode").asText("");
                    if (!Set.of(HypixelRewardAutoClaim.MODE_RARITY, HypixelRewardAutoClaim.MODE_PRIORITY).contains(mode))
                        throw new IllegalArgumentException("Invalid reward mode");
                }
                case "reward_priority" -> {
                    item = request.path("item").asText("");
                    if (!HypixelRewardAutoClaim.knownItemKeys().contains(item))
                        throw new IllegalArgumentException("Invalid reward item");
                    JsonNode priority = request.path("value");
                    if (!priority.isNull()) {
                        if (!priority.isInt() || priority.intValue() < 1 || priority.intValue() > 30)
                            throw new IllegalArgumentException("Invalid reward priority");
                        value = priority.intValue();
                    }
                }
            }
            return new Change(action, enabled, mode, item, value);
        }

        /**
         * @param userId 从已认证页面会话取得的用户标识
         * @return 设置是否保存成功
         */
        public boolean save(String userId) {
            return switch (action) {
                case "reward_enabled" -> HypixelRewardAutoClaim.setEnabled(userId, enabled);
                case "reward_mode" -> HypixelRewardAutoClaim.setFirstClaim(userId, mode);
                case "reward_priority" -> value == null
                        ? HypixelRewardAutoClaim.removeItemPriority(userId, item)
                        : HypixelRewardAutoClaim.setItemPriority(userId, item, value);
                case "keyboard" -> OfficialUsers.setUserUnsupportedKeyboard(userId, enabled);
                default -> throw new IllegalArgumentException("Invalid settings action");
            };
        }
    }

    public static MiniappUserSettings parse(String json) throws IOException {
        var mapper = new ObjectMapper();
        JsonNode root = json == null || json.isBlank() ? mapper.createObjectNode() : mapper.readTree(json);
        if (root == null || !root.isObject()) throw new IOException("Invalid user settings");
        JsonNode uid = root.path("bv_uid"), reward = root.path("hypixel_reward"), share = root.path("user_share_link");
        String bilibili = uid.isIntegralNumber() || uid.isTextual() ? uid.asText() : null;
        if (bilibili != null && !bilibili.matches("[1-9][0-9]{0,19}")) bilibili = null;
        List<Priority> priorities = new ArrayList<>();
        if (reward.path("item_priority").isObject()) {
            var fields = reward.path("item_priority").fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getValue().isInt()) priorities.add(new Priority(entry.getKey(), entry.getValue().intValue()));
            }
        }
        priorities.sort(Comparator.comparingInt(Priority::value).reversed().thenComparing(Priority::item));
        String link = text(share.path("share_url"));
        if (link != null) {
            try {
                URI uri = URI.create(link);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null)
                    link = null;
            } catch (IllegalArgumentException e) {
                link = null;
            }
        }
        var invited = new HashSet<String>();
        if (share.path("invited_user").isArray()) {
            for (JsonNode user : share.path("invited_user")) {
                String id = text(user);
                if (id != null) invited.add(id);
            }
        }
        return new MiniappUserSettings(bilibili,
                new Reward(reward.path("enabled").isBoolean() && reward.path("enabled").booleanValue(),
                        "priority".equals(reward.path("first_claim").asText()) ? "priority" : "rarity", List.copyOf(priorities),
                        HypixelRewardAutoClaim.knownItemKeys().stream().sorted().toList()),
                new Share(link, invited.size(), text(share.path("inviter_open_id")) != null, text(share.path("create_time"))),
                root.path("unsupported_keyboard").asBoolean(false));
    }

    private static String text(JsonNode value) {
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }
}
