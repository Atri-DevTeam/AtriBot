package top.yzljc.qqbot.messages;

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

/**
 * 负责接收 HTTP 请求并将消息转发给处理逻辑
 */
public class MessageReceiver {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * 启动 HTTP 监听服务
     *
     * @param port           监听端口
     * @param messageHandler 消息处理回调函数（当收到 JSON 消息时调用）
     */
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
                            System.err.println("[ERROR] JSON 解析或处理异常: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    String resp = "{\"status\":\"ok\"}";
                    exchange.sendResponseHeaders(200, resp.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, 0);
                    exchange.close();
                }
            });

            // 设置线程池
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            System.out.println("[INFO] QQ指令监听服务已启动，端口: " + port);

        } catch (IOException e) {
            System.err.println("[ERROR] 无法启动 HTTP 服务: " + e.getMessage());
        }
    }
}