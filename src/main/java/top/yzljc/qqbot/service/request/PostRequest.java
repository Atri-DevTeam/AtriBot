package top.yzljc.qqbot.service.request;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.utils.Logger;

import java.util.HashMap;
import java.util.Map;

public class PostRequest {
    private static final RestTemplate restTemplate = new RestTemplate();

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

    private static JsonNode executeRequest(RequestType type, Map<String, Object> params) {
        try {
            String BASEURL = Config.getInstance().getHttpUrl();
            String postUrl = BASEURL + type.getRequestLink();
            Map<String, Object> requestBody = params != null ? params : new HashMap<>();

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(postUrl, requestBody, JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                Logger.warn("请求失败! 接口: {}, HTTP Code: {}", type.name(), response.getStatusCode().value());
            }
        } catch (Exception e) {
            Logger.error("接口请求异常! 类型: {}, 错误: {}", type.name(), e.getMessage());
        }
        return null;
    }
}