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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
public class GroupList {

    private static final Logger log = LoggerFactory.getLogger(GroupList.class);
    
    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String GROUP_LIST_API = BASEURL + "/get_group_list";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final boolean isDebugMode = settings.isDebugMode();
    private static final long debugGroupId = settings.getDebugGroupId();

    public static Set<Long> fetchAllGroupIds() {
        Set<Long> groupIds = new HashSet<>();
        String nextToken = "";

        log.info("开始联网同步群列表……");

        try {
            while (true) {
                String reqJson = "{}";

                HttpURLConnection conn = (HttpURLConnection) new URI(GROUP_LIST_API).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                try (OutputStream out = conn.getOutputStream()) {
                    out.write(reqJson.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() != 200) {
                    log.error("API 请求失败：{}", conn.getResponseCode());
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
                break;
            }
        } catch (Exception e) {
            log.error("获取群列表异常: {}", e.getMessage());
        }

        log.info("同步完成，共获取到 {} 个群聊。", groupIds.size());

        // 修改点：如果是 Debug 模式，即使扫描到了所有群，也只返回 Debug 群号
        if (isDebugMode) {
            log.info("Debug 模式已开启，仅返回调试群号: {}", debugGroupId);
            Set<Long> debugSet = new HashSet<>();
            debugSet.add(debugGroupId);
            return debugSet;
        }

        return groupIds;
    }
}
