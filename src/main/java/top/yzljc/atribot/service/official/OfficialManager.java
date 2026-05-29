package top.yzljc.atribot.service.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.socket.OfficialWebSocketClient;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class OfficialManager {

    private final String apiBaseUrl;
    private final OfficialTokenManager tokenManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private OfficialWebSocketClient webSocketClient;

    public OfficialManager(String apiBaseUrl, OfficialTokenManager tokenManager) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
    }

    public void start() throws Exception {
        log.info("正在初始化 QQ Bot...");

        String accessToken = tokenManager.getAccessToken();

        String gatewayUrl = getGateway(accessToken);
        log.info("获取 Gateway URL 成功: {}", gatewayUrl);

        webSocketClient = new OfficialWebSocketClient(new URI(gatewayUrl), tokenManager);
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