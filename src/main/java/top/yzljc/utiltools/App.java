package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    private static final String NAPCAT_API = "http://106.14.23.232:8848/send_group_msg";
    private static final int LISTEN_PORT = 37142;
    // 新增：QQ机器人监听的端口
    private static final int QQ_BOT_PORT = 8851;

    private static final String LIST_FILE = "serverlist.txt";
    private static final Map<String, SInfo> serverMap = new HashMap<>();

    private enum ServerState {
        OFFLINE("离线"),
        ONLINE("在线");
        final String desc;
        ServerState(String desc) { this.desc = desc; }
    }

    private static class SInfo {
        long groupId;
        String name;
        String ip;
        int port;
        String id;

        SInfo(long g, String n, String i, int p, String id) {
            groupId = g;
            name = n;
            ip = i;
            port = p;
            this.id = id;
        }
    }

    public static void main(String[] args) {
        System.out.println("==== Minecraft Server Monitor ====");
        SendLike.start(QQ_BOT_PORT);

        // 2. 加载配置到内存
        loadServers();
        if (serverMap.isEmpty()) {
            System.err.println("未读取到服务器配置，请检查 serverlist.txt");
            System.err.println("格式: 群号/名称/IP/端口/编号#...");
            return;
        }
        System.out.printf("已加载 %d 个服务器配置。\n", serverMap.size());
        startSocketServer();
    }

    private static void loadServers() {
        serverMap.clear();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(LIST_FILE), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) return;

            if (line.startsWith("\uFEFF")) line = line.substring(1);

            String[] items = line.split("#");
            for (String item : items) {
                String[] fs = item.split("/");
                if (fs.length != 5) {
                    System.err.println("格式错误跳过: " + item);
                    continue;
                }
                try {
                    long gid = Long.parseLong(fs[0].trim());
                    String name = fs[1].trim();
                    String ip = fs[2].trim();
                    int port = Integer.parseInt(fs[3].trim());
                    String id = fs[4].trim();

                    serverMap.put(id, new SInfo(gid, name, ip, port, id));
                    System.out.printf("  -> 加载配置: [%s] %s (群:%d)\n", id, name, gid);
                } catch (Exception e) {
                    System.err.println("解析错误: " + item);
                }
            }
        } catch (Exception e) {
            System.err.println("读取配置文件失败: " + e.getMessage());
        }
    }

    private static void startSocketServer() {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            System.out.println("正在监听Socket端口: " + LISTEN_PORT + "，等待插件连接...");

            while (true) {
                Socket client = serverSocket.accept();
                threadPool.submit(() -> handleClient(client));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream()) {
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            if (len > 0) {
                String rawData = new String(buffer, 0, len, StandardCharsets.UTF_8).trim();
                System.out.println("收到Socket数据: " + rawData);

                String[] parts = rawData.split("\\|");
                if (parts.length >= 2) {
                    String receivedId = parts[0];
                    String statusStr = parts[1];
                    SInfo serverInfo = serverMap.get(receivedId);

                    if (serverInfo != null) {
                        ServerState state = "ONLINE".equalsIgnoreCase(statusStr) ? ServerState.ONLINE : ServerState.OFFLINE;
                        if (state == ServerState.ONLINE) {
                            System.out.printf("[%s] 服务器上线，准备进行推送...\n", serverInfo.name);
                        }
                        boolean ok = sendToQQBot(serverInfo, state);
                        System.out.printf("[%s] 处理完毕: %s -> 推送%s\n", serverInfo.name, state.desc, ok ? "成功" : "失败");
                    } else {
                        System.err.println("收到未知服务器ID的数据: " + receivedId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("处理Socket连接异常: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static boolean sendToQQBot(SInfo server, ServerState state) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            String textContent = String.format(
                    "[!] 服务器状态更新\n服务器：%s\n地址：%s:%d\n状态：%s",
                    server.name, server.ip, server.port, state.desc
            );

            Map<String, Object> textData = new HashMap<>();
            textData.put("text", textContent);
            Map<String, Object> textNode = new HashMap<>();
            textNode.put("type", "text");
            textNode.put("data", textData);

            String imgUrl = String.format(
                    "https://api.mcstatus.io/v2/widget/java/%s:%d?dark=true&rounded=true&ts=%d",
                    server.ip, server.port, System.currentTimeMillis()
            );

            Map<String, Object> imgData = new HashMap<>();
            imgData.put("name", "status_img");
            imgData.put("url", imgUrl);
            Map<String, Object> imgNode = new HashMap<>();
            imgNode.put("type", "image");
            imgNode.put("data", imgData);

            Object[] messageList = new Object[]{textNode, imgNode};

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", server.groupId);
            payloadMap.put("message", messageList);

            String payload = objectMapper.writeValueAsString(payloadMap);

            HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            conn.getInputStream().close();
            return code == 200;

        } catch (Exception ex) {
            System.err.println("推送QQ bot异常: " + ex.getMessage());
            return false;
        }
    }
}