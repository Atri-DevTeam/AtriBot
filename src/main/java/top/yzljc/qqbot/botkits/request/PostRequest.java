package top.yzljc.qqbot.botkits.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class PostRequest {
    private static final Logger log = LoggerFactory.getLogger(PostRequest.class);
    private static final Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static JsonNode executeRequest(RequestType type, Map<String, Object> params) {
        HttpURLConnection conn = null;
        try {
            String postUrl = BASEURL + type.getRequestLink();
            byte[] payload = jsonMapper.writeValueAsBytes(params != null ? params : new HashMap<>());

            conn = (HttpURLConnection) new URI(postUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                try (InputStream is = conn.getInputStream()) {
                    return jsonMapper.readTree(is);
                }
            } else {
                log.warn("请求失败! 接口: {}, HTTP Code: {}", type.name(), code);
            }
        } catch (Exception e) {
            log.error("接口请求异常! 类型: {}, 错误: {}", type.name(), e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /**
     * 获取 API 返回结果 (多参)
     */
    public static JsonNode getPostResult(RequestType type, Map<String, Object> params) {
        return executeRequest(type, params);
    }

    /**
     * 获取 API 返回结果 (单参)
     * 用法: getSimplePostResult(CheckType.GET_GROUP_INFO, "group_id", 123456L)
     */
    public static JsonNode getSimplePostResult(RequestType type, String key, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(key, value);
        return executeRequest(type, params);
    }

    /**
     * 获取 API 返回结果 (无参数)
     */
    public static JsonNode getPostResult(RequestType type) {
        return executeRequest(type, null);
    }

    /**
     * 发送 POST 请求 (无返回值)
     */
    public static void sendPost(RequestType type, Map<String, Object> params) {
        executeRequest(type, params);
    }

    /**
     * 发送 POST 请求 (忽略返回值, 单参)
     * 用法: sendSimplePost(CheckType.RECALL_MESSAGE, "message_id", 123L)
     */
    public static void sendSimplePost(RequestType type, String key, Object value) {
        Map<String, Object> params = new HashMap<>();
        params.put(key, value);
        executeRequest(type, params);
    }
}