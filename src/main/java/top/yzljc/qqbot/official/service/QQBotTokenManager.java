package top.yzljc.qqbot.official.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class QQBotTokenManager {

    @Value("${qqbot.app-id}")
    private String appId;

    @Value("${qqbot.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    
    private String currentAccessToken;
    private long expireTime = 0;

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

        try {
            JsonNode response = restTemplate.postForObject(tokenUrl, requestBody, JsonNode.class);
            if (response != null && response.has("access_token")) {
                this.currentAccessToken = response.get("access_token").asText();
                long expiresIn = response.get("expires_in").asLong() - 600;
                this.expireTime = System.currentTimeMillis() + (expiresIn * 1000);
                log.info("新 Token 获取成功！有效期至: {}", this.expireTime);
            } else {
                log.error("获取 Token 失败，接口返回: {}", response);
            }
        } catch (Exception e) {
            log.error("请求腾讯 Token 接口异常", e);
        }
    }
}