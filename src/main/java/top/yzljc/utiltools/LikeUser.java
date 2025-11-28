package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class LikeUser {
    public static final ObjectMapper jsonMapper = new ObjectMapper();
    public static final String NAPCAT_LIKE_API = "http://106.14.23.232:8848/send_like";
    public static final String NAPCAT_GROUP_API = "http://106.14.23.232:8848/send_group_msg";

    public static void registerToServer(HttpServer server) {
        server.createContext("/", (HttpExchange exchange) -> {
            String resp = "{\"status\":\"ok\"}";
            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    InputStream is = exchange.getRequestBody();
                    byte[] bodyBytes = is.readAllBytes();
                    String body = new String(bodyBytes, StandardCharsets.UTF_8);

                    JsonNode json = jsonMapper.readTree(body);
                    long userId = json.path("user_id").asLong();
                    long groupId = json.path("group_id").asLong();
                    String rawMessage = json.path("raw_message").asText();
                    String msg = rawMessage.trim().toLowerCase();
                    String[] keywords = {"赞我", "zanwo", "likeme"};

                    for (String kw : keywords) {
                        if (msg.equalsIgnoreCase(kw)) {
                            sendLike(userId, groupId);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                resp = "{\"status\":\"fail\"}";
                System.err.println("[Likeuser] 请求处理异常: " + e.getMessage());
            }
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
    }
    public static void sendLike(long userId, long groupId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            String likeResult = "点赞成功！";
            try {
                String payload = String.format("{\"user_id\":\"%s\",\"times\":10}", userId);
                System.out.println("[Likeuser] 准备点赞 => QQ: " + userId);
                HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_LIKE_API).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
                String respStr = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.getInputStream().close();

                JsonNode respJson = null;
                try { respJson = jsonMapper.readTree(respStr); } catch (Exception ignored) {}
                if (respJson != null) {
                    String status = respJson.path("status").asText();
                    String msg = respJson.path("msg").asText("");
                    if ("ok".equalsIgnoreCase(status)) {
                        likeResult = "点赞成功！(+10 Social Credits!)";
                        System.out.println("[Likeuser] 点赞成功 => QQ: " + userId);
                    }
                    else if (status.contains("fail")) {
                        likeResult = "点赞失败，可能是由于该用户今日已被赞过啦~";
                        System.out.println("[Likeuser] 点赞失败 => QQ: " + userId + " | msg: " + msg);
                    } else {
                        System.out.println("[Likeuser] 点赞未知响应 => QQ: " + userId + " | 原始: " + respStr);
                    }
                } else {
                    System.out.println("[Likeuser] 点赞接口返回非预期格式 => QQ: " + userId + " | 原始: " + respStr);
                }
                sendGroupMsg(groupId, likeResult);
            } catch (Exception ex) {
                sendGroupMsg(groupId,"点赞接口异常，请稍后再试。");
                System.err.println("[Likeuser] 点赞接口异常: " + ex.getMessage());
            }
        });
    }

    public static void sendGroupMsg(long groupId, String text) {
        try {
            var textNode = Collections.singletonMap("type", "text");
            var textData = Collections.singletonMap("text", text);
            var node = new java.util.HashMap<String, Object>(textNode);
            node.put("data", textData);
            var payloadMap = new java.util.HashMap<String, Object>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", Collections.singletonList(node));
            String payload = jsonMapper.writeValueAsString(payloadMap);

            HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_GROUP_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            conn.getInputStream().close();
            System.out.println("[Likeuser] 群反馈已发送 => groupId: " + groupId + " 内容: " + text);
        } catch (Exception e) {
            System.err.println("[Likeuser] 群消息发送失败: " + e.getMessage());
        }
    }
}