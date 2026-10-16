package top.yzljc.atricore.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * QQ 官方机器人连接管理器，WebSocket 由本类启动，Webhook 由 Provider 接收
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialManager
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.platform.qq
 */
@Slf4j
public class OfficialManager {

    private final String apiBaseUrl;
    private final TokenManager tokenManager;
    private final boolean webhookMode;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebSocketClient webSocketClient;

    /**
     * 创建使用 WebSocket 的 QQ 连接管理器
     *
     * @param apiBaseUrl   QQ API 根地址
     * @param tokenManager 共享令牌服务
     */
    public OfficialManager(String apiBaseUrl, TokenManager tokenManager) {
        this(apiBaseUrl, tokenManager, "ws");
    }

    /**
     * 创建指定接入模式的 QQ 连接管理器
     *
     * @param apiBaseUrl     QQ API 根地址
     * @param tokenManager   共享令牌服务
     * @param connectionMode webhook 使用 HTTP 回调，其余值使用 WebSocket
     */
    public OfficialManager(String apiBaseUrl, TokenManager tokenManager, String connectionMode) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
        this.webhookMode = "webhook".equalsIgnoreCase(connectionMode);
    }

    /**
     * 查询网关并发起 WebSocket 连接，Webhook 模式仅记录等待状态
     * <p>本方法不检查 QQ 平台开关，运行服务应在调用前判断配置
     *
     * @throws Exception 获取网关或创建连接失败
     */
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

    /**
     * 关闭当前 WebSocket 连接及内部任务，不关闭 Provider 的 HTTP 服务
     *
     */
    public void stop() {
        if (webSocketClient != null) {
            webSocketClient.shutdown();
            webSocketClient = null;
        }
    }

    private String getGateway(String accessToken) throws Exception {
        String gatewayApi = apiBaseUrl + "/gateway";

        HttpRequest request = HttpService.newRequestBuilder()
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
