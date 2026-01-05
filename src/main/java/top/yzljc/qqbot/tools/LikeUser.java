package top.yzljc.qqbot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class LikeUser {
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String NAPCAT_LIKE_API = BASEURL + "/send_like";

    // 触发关键词
    private static final String[] KEYWORDS = {"赞我", "zanwo", "likeme"};

    /**
     * 处理点赞指令
     * @param json 原始消息 JSON
     */
    public static void processCommand(JsonNode json) {
        // 1. 基本校验
        if (!json.has("message_type") || !"group".equals(json.path("message_type").asText())) {
            return;
        }

        String rawMessage = json.path("raw_message").asText();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }

        String msgLower = rawMessage.trim().toLowerCase();
        for (String kw : KEYWORDS) {
            if (msgLower.equalsIgnoreCase(kw)) {
                long userId = json.path("user_id").asLong();
                long groupId = json.path("group_id").asLong();

                // 触发点赞逻辑
                sendLike(userId, groupId);
                return;
            }
        }
    }

    /**
     * 执行点赞操作
     * @param userId 被点赞的QQ
     * @param groupId 来源群组（用于发送反馈消息）
     */
    public static void sendLike(long userId, long groupId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            String likeResult = "点赞成功！";
            try {
                // 构造点赞请求 (点赞10次)
                String payload = String.format("{\"user_id\":\"%s\",\"times\":10}", userId);
                System.out.println("[INFO] 准备点赞 => QQ: " + userId);

                HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_LIKE_API).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));

                String respStr = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.getInputStream().close();

                JsonNode respJson = null;
                try {
                    respJson = jsonMapper.readTree(respStr);
                } catch (Exception ignored) {}

                if (respJson != null) {
                    String status = respJson.path("status").asText();
                    String msg = respJson.path("msg").asText("");

                    if ("ok".equalsIgnoreCase(status)) {
                        likeResult = "点赞成功！(+10 Social Credits!)";
                        System.out.println("[INFO] 点赞成功 => QQ: " + userId);
                    }
                    else if (status.contains("fail")) {
                        likeResult = "点赞失败，可能是由于该用户今日已被赞过啦~";
                        System.out.println("[INFO] 点赞失败 => QQ: " + userId + " | msg: " + msg);
                    } else {
                        System.out.println("[INFO] 点赞未知响应 => QQ: " + userId + " | 原始: " + respStr);
                    }
                } else {
                    System.out.println("[INFO] 点赞接口返回非预期格式 => QQ: " + userId + " | 原始: " + respStr);
                }

                MessageSender.sendGroupMessage(groupId, likeResult);

            } catch (Exception ex) {
                MessageSender.sendGroupMessage(groupId, "点赞接口异常，请稍后再试。");
                System.err.println("[INFO] 点赞接口异常: " + ex.getMessage());
            }
        });
    }
}