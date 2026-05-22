package top.yzljc.qqbot.official.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.service.request.HttpService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class QQBotTokenManager {

    private final String appId;
    private final String clientSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String currentAccessToken;
    private long expireTime = 0;

    public QQBotTokenManager(String appId, String clientSecret) {
        this.appId = appId;
        this.clientSecret = clientSecret;
    }

    public synchronized String getAccessToken() {
        if (currentAccessToken == null || System.currentTimeMillis() > expireTime) {
            log.info("Token为空或已过期，准备向腾讯服务器请求新 Token...");
            refreshToken();
        }
        return currentAccessToken;
    }

    private void refreshToken() {
        String tokenUrl = "https://bots.qq.com/app/getAppAccessToken";

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("appId", appId);
        requestBody.put("clientSecret", clientSecret);

        log.debug(requestBody.toString());

        try {
            String json = objectMapper.writeValueAsString(requestBody);
            JsonNode response = HttpService.postJson(tokenUrl, json);

            if (response != null && response.has("access_token")) {
                this.currentAccessToken = response.get("access_token").asText();

                long expiresIn = response.get("expires_in").asLong();

                long buffer = 600;
                if (expiresIn <= buffer) {
                    buffer = expiresIn / 2;
                }

                long validSeconds = expiresIn - buffer;

                this.expireTime = System.currentTimeMillis() + (validSeconds * 1000);
                log.info("新 Token 获取成功！有效期至: {}", this.expireTime);
            } else {
                log.error("获取 Token 失败，接口返回: {}", response);
            }
        } catch (JsonProcessingException e) {
            log.error("请求腾讯 Token 接口序列化失败", e);
        }
    }
}