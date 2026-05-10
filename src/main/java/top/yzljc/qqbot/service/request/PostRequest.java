package top.yzljc.qqbot.service.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.config.Config;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PostRequest {
    private static final ObjectMapper mapper = new ObjectMapper();

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

            String json = mapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(postUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    return mapper.readTree(body);
                }
                return null;
            } else {
                log.warn("请求失败! 接口: {}, HTTP Code: {}", type.name(), response.statusCode());
            }
        } catch (Exception e) {
            log.error("接口请求异常! 类型: {}, 错误: {}", type.name(), e.getMessage());
        }
        return null;
    }
}