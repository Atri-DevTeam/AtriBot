package top.yzljc.qqbot.botkits.request;

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

public class PostDataRequest {
    private static final Logger log = LoggerFactory.getLogger(PostDataRequest.class);
    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static String sendSimplePost(CheckType checkType, long checkData) {
        try {

            String postUrl = BASEURL + checkType.getRequestLink();
            String payload = String.format("{\"%s\":\"%d\"}", checkType.getRequestDataType(), checkData);

            HttpURLConnection conn = (HttpURLConnection) new URI(postUrl).toURL().openConnection();
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
            log.warn("Request Error! Request Type: {}, Data: {}, Error: {}", checkType, checkData, e.getMessage());
        }
        return null;
    }

    private static JsonNode tranJson(String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            log.warn("原始数据在转化为JsonNode类型时发生错误: {}", e.getMessage());
            return null;
        }
    }

    public static JsonNode getSimplePostResult(CheckType checkType, long checkData) {
        String response = sendSimplePost(checkType, checkData);
        return tranJson(response);
    }
}
