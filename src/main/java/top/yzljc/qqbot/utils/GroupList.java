package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class GroupList {

    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String GROUP_LIST_API = BASEURL + "/get_group_list";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 获取所有群号，返回 Set<Long>
     */
    public static Set<Long> fetchAllGroupIds() {
        Set<Long> groupIds = new HashSet<>();
        String nextToken = "";

        try {
            while (true) {
                // 构建请求体，处理分页 token
                String reqJson = "{\"next_token\":\"" + nextToken + "\"}";

                HttpURLConnection conn = (HttpURLConnection) new URL(GROUP_LIST_API).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                try (OutputStream out = conn.getOutputStream()) {
                    out.write(reqJson.getBytes(StandardCharsets.UTF_8));
                }

                try (InputStream in = conn.getInputStream()) {
                    JsonNode resp = JSON_MAPPER.readTree(in);

                    // 解析 data 数组
                    if (resp.has("data") && resp.get("data").isArray()) {
                        for (JsonNode group : resp.get("data")) {
                            if (group.has("group_id")) {
                                try {
                                    long gid = group.get("group_id").asLong();
                                    groupIds.add(gid);
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    // 处理分页逻辑
                    if (resp.has("next_token")) {
                        String token = resp.get("next_token").asText();
                        if (token == null || token.isEmpty()) {
                            break;
                        } else {
                            nextToken = token;
                        }
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[INFO] 获取群列表异常: " + e.getMessage());
        }

        System.out.println("[INFO] 共获取到 " + groupIds.size() + " 个群聊。");
        return groupIds;
    }
}