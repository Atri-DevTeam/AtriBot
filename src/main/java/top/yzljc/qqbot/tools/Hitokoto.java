package top.yzljc.qqbot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.GroupConfigManager;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class Hitokoto {
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final String API_URL = "https://v1.hitokoto.cn/";
    static Settings settings = Config.getInstance();
    private static final String[] KEYWORDS = settings.getKeywordsHitokoto();

    public static void process(JsonNode json) {
        if (!json.has("group_id") || !json.has("raw_message")) return;

        long groupId = json.path("group_id").asLong();
        String rawMessage = json.path("raw_message").asText().trim().toLowerCase();

        if (!GroupConfigManager.isFeatureEnabled(groupId, "one_text")) return;

        if (!containsKeyword(rawMessage)) return;

        Executors.newSingleThreadExecutor().submit(() -> {
            String feedback = fetchHitokoto();
            MessageSender.sendGroupMessage(groupId, feedback);
        });
    }

    private static String fetchHitokoto() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String respStr = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode respJson = jsonMapper.readTree(respStr);

            String hitokoto = respJson.path("hitokoto").asText();
            String from = respJson.path("from").asText();
            JsonNode fromWhoNode = respJson.path("from_who");

            StringBuilder result = new StringBuilder(hitokoto).append("\n—— ").append(from);

            if (!fromWhoNode.isNull() && !fromWhoNode.asText().isEmpty()) {
                result.append(" · ").append(fromWhoNode.asText());
            }

            System.out.println("[INFO] 一言发送成功");
            return result.toString();
        
        } catch (Exception ex) {
            System.err.println("[ERROR] 一言获取异常: " + ex.getMessage());
            return "[ERROR] 一言获取失败：接口异常。";
        }
    }

    private static boolean containsKeyword(String msg) {
        for (String kw : KEYWORDS)
            if (msg.contains(kw)) return true;
        return false;
    }
}
