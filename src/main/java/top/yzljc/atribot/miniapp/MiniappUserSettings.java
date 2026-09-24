package top.yzljc.atribot.miniapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * 将用户已有设置转换为个人档案的可读字段
 *
 * @Author YZ_Ljc_
 * @ClassName MiniappUserSettings
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
public record MiniappUserSettings(String bilibiliUid, Reward reward, Share share) {
    public record Priority(String item, int value) {
    }

    public record Reward(boolean enabled, String mode, List<Priority> priorities) {
    }

    public record Share(String url, int invitedCount, boolean invitedBySomeone, String createdAt) {
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
                        "priority".equals(reward.path("first_claim").asText()) ? "priority" : "rarity", List.copyOf(priorities)),
                new Share(link, invited.size(), text(share.path("inviter_open_id")) != null, text(share.path("create_time"))));
    }

    private static String text(JsonNode value) {
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }
}
