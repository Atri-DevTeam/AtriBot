package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.request.HttpService;

import java.util.Map;

/**
 * Instance-local QQ access-token cache for a group-only bot.
 */
@Slf4j
public final class GroupBotTokenManager {

    private static final String TOKEN_URL = "https://api.bot.qq.com/app/getAppAccessToken";

    private final String instanceKey;
    private final String appId;
    private final String clientSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String currentAccessToken;
    private long expireTime;

    GroupBotTokenManager(String instanceKey, String appId, String clientSecret) {
        this.instanceKey = instanceKey;
        this.appId = appId;
        this.clientSecret = clientSecret;
    }

    public synchronized String getAccessToken() {
        if (currentAccessToken == null || System.currentTimeMillis() >= expireTime) {
            refreshToken();
        }
        return currentAccessToken;
    }

    private void refreshToken() {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "appId", appId,
                    "clientSecret", clientSecret
            ));
            JsonNode response = HttpService.postJson(TOKEN_URL, json);
            if (response == null || !response.hasNonNull("access_token")) {
                currentAccessToken = null;
                expireTime = 0;
                log.error("QQ 群聊 Bot 实例 {} 获取 Token 失败，接口返回: {}", instanceKey, response);
                return;
            }

            currentAccessToken = response.path("access_token").asText();
            long expiresIn = response.path("expires_in").asLong(0);
            long buffer = expiresIn <= 600 ? Math.max(1, expiresIn / 2) : 600;
            expireTime = System.currentTimeMillis() + Math.max(1, expiresIn - buffer) * 1000;
            log.info("QQ 群聊 Bot 实例 {} 的 Token 已刷新", instanceKey);
        } catch (Exception e) {
            currentAccessToken = null;
            expireTime = 0;
            log.error("QQ 群聊 Bot 实例 {} 获取 Token 失败", instanceKey, e);
        }
    }
}
