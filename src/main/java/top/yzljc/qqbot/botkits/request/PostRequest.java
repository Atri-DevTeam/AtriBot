package top.yzljc.qqbot.botkits.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PostRequest {
    private static final Logger log = LoggerFactory.getLogger(PostRequest.class);
    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static void postRequest(CheckType checkType, long checkData) {
        HttpURLConnection conn = null;
        try {
            String postUrl = BASEURL + checkType.getRequestLink();
            String payload = String.format("{\"%s\":\"%d\"}", checkType.getRequestDataType(), checkData);

            conn = (HttpURLConnection) new URI(postUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (!(responseCode >= 200 && responseCode < 300)) {
                log.warn("Request failed with status code: {}", responseCode);
                try (InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        log.warn("Error response: {}", new String(es.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Request Error! Request Type: {}, Data: {}, Error: {}", checkType, checkData, e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static void sendSimplePost(CheckType checkType, long checkData) {
        postRequest(checkType, checkData);
    }

    public static void sendPoke(long groupId, long userId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("group_id", groupId);
            data.put("user_id", userId);

            String payload = jsonMapper.writeValueAsString(data);
            String pokeUrl = BASEURL + "/send_group_poke";

            HttpURLConnection conn = (HttpURLConnection) new URI(pokeUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getInputStream() != null) {
                conn.getInputStream().close();
            }

        } catch (Exception e) {
            log.warn("戳一戳发送失败: {}", e.getMessage());
        }
    }
}