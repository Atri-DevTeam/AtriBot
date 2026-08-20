package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class OfficialManager {

    private final String apiBaseUrl;
    private final TokenManager tokenManager;
    private final boolean webhookMode;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocketClient webSocketClient;

    public OfficialManager(String apiBaseUrl, TokenManager tokenManager) {
        this(apiBaseUrl, tokenManager, "ws");
    }

    public OfficialManager(String apiBaseUrl, TokenManager tokenManager, String connectionMode) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
        this.webhookMode = "webhook".equalsIgnoreCase(connectionMode);
    }

    public void start() throws Exception {
        if (webhookMode) {
            log.info("[!] 当前官机接入模式为Webhook，等待开放平台下发事件...");
            return;
        }
        log.info("正在初始化 QQ Bot...");

        String accessToken = tokenManager.getAccessToken();

        String gatewayUrl = getGateway(accessToken);
        log.info("获取 Gateway URL 成功: {}", gatewayUrl);

        webSocketClient = new WebSocketClient(new URI(gatewayUrl), tokenManager);
        webSocketClient.connect();
    }

    public void stop() {
        if (webSocketClient != null) {
            webSocketClient.shutdown();
            webSocketClient = null;
        }
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
