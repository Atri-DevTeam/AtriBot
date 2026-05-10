package top.yzljc.qqbot.official.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class QQBotManagerService {

    private final String apiBaseUrl;
    private final QQBotTokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private QQBotWebSocketClient webSocketClient;

    public QQBotManagerService(String apiBaseUrl, QQBotTokenManager tokenManager) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
    }

    public void start() throws Exception {
        log.info("正在初始化 QQ Bot...");

        String accessToken = tokenManager.getAccessToken();

        String gatewayUrl = getGateway(accessToken);
        log.info("获取 Gateway URL 成功: {}", gatewayUrl);

        webSocketClient = new QQBotWebSocketClient(new URI(gatewayUrl), tokenManager);
        webSocketClient.connect();
    }

    private String getGateway(String accessToken) throws Exception {
        String gatewayApi = apiBaseUrl + "/gateway";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayApi))
                .header("Authorization", "QQBot " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode responseNode = objectMapper.readTree(response.body());

        if (responseNode.has("url")) {
            return responseNode.get("url").asText();
        }
        throw new RuntimeException("获取 Gateway 失败: " + responseNode);
    }
}