package top.yzljc.qqbot.botkits.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageReceiver {

    private static final Logger log = LoggerFactory.getLogger(MessageReceiver.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static void start(int port, Consumer<JsonNode> messageHandler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/", (HttpExchange exchange) -> {
                try {
                    InputStream is = exchange.getRequestBody();
                    byte[] bodyBytes = is.readAllBytes();
                    String body = new String(bodyBytes, StandardCharsets.UTF_8);

                    if (!body.isEmpty()) {
                        try {
                            JsonNode root = jsonMapper.readTree(body);
                            if (messageHandler != null) {
                                messageHandler.accept(root);
                            }
                        } catch (Exception e) {
                            log.error("JSON 解析或处理异常：{}", e.getMessage());
                        }
                    }

                    String resp = "{\"status\":\"ok\"}";
                    exchange.sendResponseHeaders(200, resp.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes());
                    }
                } catch (Exception e) {
                    log.error("请求发送失败：{}", e.getMessage());
                    exchange.sendResponseHeaders(500, 0);
                    exchange.close();
                }
            });

            // 设置线程池
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            log.info("前端消息监听服务已启动，端口：{}", port);
        } catch (IOException e) {
            log.error("无法启动 HTTP 服务：{}", e.getMessage());
        }
    }
}
