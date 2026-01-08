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
    private static final String[] KEYWORDS = {"一言", "yiyan", "hitokoto"};

    static Settings settings = Config.getInstance();
    private static final java.util.List<Long> admins = settings.getAdminUids();

    public static void process(JsonNode json) {
        if (!json.has("group_id") || !json.has("raw_message")) return;

        long groupId = json.path("group_id").asLong();
        long userId = json.path("user_id").asLong();
        String rawMessage = json.path("raw_message").asText().trim().toLowerCase();

        if (!GroupConfigManager.isFeatureEnabled(groupId, "one_text")) return;

        if (!containsKeyword(rawMessage)) return;
        if (!admins.contains(userId)) {
            if (!GroupConfigManager.isFeatureEnabled(groupId,"hitokoto")) {
                return;
            }
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            String feedback;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String respStr = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.getInputStream().close();

                JsonNode respJson = jsonMapper.readTree(respStr);
                
                String hitokoto = respJson.path("hitokoto").asText();
                String from = respJson.path("from").asText();
                String from_who = respJson.path("from_who").asText();
                
                feedback = String.format("%s\n—— %s%s", 
                    hitokoto, 
                    from,
                    from_who != null && !from_who.isEmpty() ? " · " + from_who : ""
                );
                
                System.out.println("[INFO] 一言发送成功");
            } catch (Exception ex) {
                feedback = "[一言获取失败] 接口异常。";
                System.err.println("[INFO] 一言获取异常: " + ex.getMessage());
            }

            MessageSender.sendGroupMessage(groupId, feedback);
        });
    }

    private static boolean containsKeyword(String msg) {
        for (String kw : KEYWORDS)
            if (msg.contains(kw)) return true;
        return false;
    }
}
