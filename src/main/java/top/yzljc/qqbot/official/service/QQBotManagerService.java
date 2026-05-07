package top.yzljc.qqbot.official.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class QQBotManagerService implements CommandLineRunner {

    @Value("${qqbot.api-base-url}")
    private String apiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private final QQBotTokenManager tokenManager;

    private QQBotWebSocketClient webSocketClient;

    @Override
    public void run(String... args) throws Exception {
        log.info("正在初始化 QQ Bot...");

        String accessToken = tokenManager.getAccessToken();

        String gatewayUrl = getGateway(accessToken);
        log.info("获取 Gateway URL 成功: {}", gatewayUrl);

        webSocketClient = new QQBotWebSocketClient(new URI(gatewayUrl), tokenManager);
        webSocketClient.connect();
    }

    private String getGateway(String accessToken) {
        String gatewayApi = apiBaseUrl + "/gateway";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "QQBot " + accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        JsonNode response = restTemplate.exchange(
                gatewayApi,
                org.springframework.http.HttpMethod.GET,
                entity,
                JsonNode.class
        ).getBody();

        if (response != null && response.has("url")) {
            return response.get("url").asText();
        }
        throw new RuntimeException("获取 Gateway 失败: " + response);
    }
}