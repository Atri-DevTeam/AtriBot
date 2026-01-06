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
     * 供 GroupConfigManager 调用以进行配置同步
     */
    public static Set<Long> fetchAllGroupIds() {
        Set<Long> groupIds = new HashSet<>();
        String nextToken = "";

        System.out.println("[GroupList] 开始联网同步群列表...");

        try {
            while (true) {
                // 构建请求体，处理分页 token (NapCat/OneBot11 标准可能不需要token，但保留以兼容)
                String reqJson = "{}";

                HttpURLConnection conn = (HttpURLConnection) new URL(GROUP_LIST_API).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                try (OutputStream out = conn.getOutputStream()) {
                    out.write(reqJson.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() != 200) {
                    System.err.println("[GroupList] API 请求失败: " + conn.getResponseCode());
                    break;
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
                    } else {
                        break; // 没有数据了
                    }
                }

                // 通常 get_group_list 不需要分页，一次性返回，或者不需要 token
                // 如果你的 NapCat 环境确实需要分页，保留原有逻辑，否则直接 break
                break;
            }
        } catch (Exception e) {
            System.err.println("[GroupList] 获取群列表异常: " + e.getMessage());
        }

        System.out.println("[GroupList] 同步完成，共获取到 " + groupIds.size() + " 个群聊。");
        return groupIds;
    }
}