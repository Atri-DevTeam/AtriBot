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

public class GetUserName {

    private static final Logger log = LoggerFactory.getLogger(GetUserName.class);
    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final String GET_USER_INFO_URL = BASEURL + "/get_stranger_info";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static String sendCheckPost(long userId) {
        try {
            String payload = "{\"user_id\":\"" + userId + "\"}";

            HttpURLConnection conn = (HttpURLConnection) new URI(GET_USER_INFO_URL).toURL().openConnection();
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
            log.warn("获取用户信息请求失败，user_id = {}：{}", userId, e.getMessage());
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

    public static String getUserName(long userId) {
        String response = sendCheckPost(userId);
        JsonNode json = null;
        if (response != null) {
            json = parseJson(response);
        }
        if (json != null) {
            return json.path("data").path("nick").asText();
        }
        return null;
    }
}