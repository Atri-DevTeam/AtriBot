package top.yzljc.qqbot.botkits.seizeinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class GetGroupName {

    private static final Logger log = LoggerFactory.getLogger(GetUserName.class);
    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String GET_GROUP_INFO_URL = BASEURL + "/get_group_info";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static String sendCheckPost(long groupId) {
        try {
            String payload = "{\"group_id\":\"" + groupId + "\"}";

            HttpURLConnection conn = (HttpURLConnection) new URI(GET_GROUP_INFO_URL).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }

        } catch (Exception e) {
            log.warn("获取群聊信息请求失败，group_id = {}：{}", groupId, e.getMessage());
        }
        return null;
    }

    private static JsonNode parseJson(String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            log.warn("字符串转JsonNode失败: {}", e.getMessage());
            return null;
        }
    }

    public static String getGroupName(long groupId) {
        String response = sendCheckPost(groupId);
        JsonNode json = null;
        if (response != null) {
            json = parseJson(response);
        }
        if (json != null) {
            return json.path("data").path("group_name").asText();
        }
        return null;
    }
}