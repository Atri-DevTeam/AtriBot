package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    private static final String NAPCAT_API = "http://106.14.23.232:8848/send_group_msg";
    private static final int LISTEN_PORT = 37142;
    private static final int QQ_BOT_PORT = 8851;
    private static final String LIST_FILE = "serverlist.txt";

    private static final Map<String, SInfo> serverMap = new HashMap<>();
    private static final Map<String, Socket> activeConnections = new ConcurrentHashMap<>();

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
        System.out.println("==== Minecraft Server Monitor (Socket Edition) ====");
        SendLike.start(QQ_BOT_PORT);

        loadServers();
        if (serverMap.isEmpty()) {
            System.err.println("未读取到服务器配置，请检查 serverlist.txt");
            System.err.println("格式: 群号/名称/IP/端口/编号#...");
            return;
        }
        System.out.printf("已加载 %d 个服务器配置。\n", serverMap.size());
        startSocketServer();
    }
    public static boolean sendCommand(String serverId, String command, String secret) {
        Socket client = activeConnections.get(serverId);
        if (client == null || client.isClosed()) {
            System.err.println("[App] 发送失败，目标服务器未连接: " + serverId);
            return false;
        }
        try {
            String payload = "EXEC_CMD|" + command + "|" + secret;
            OutputStream out = client.getOutputStream();
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            System.err.println("[App] Socket 发送异常: " + e.getMessage());
            activeConnections.remove(serverId);
            return false;
        }
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
        String currentServerId = null; // 记录当前 Socket 对应的 ServerID

        try (InputStream in = socket.getInputStream()) {
            byte[] buffer = new byte[8192];
            int len;

            // 【关键修改】使用 while 循环保持长连接读取
            while ((len = in.read(buffer)) != -1) {
                String rawData = new String(buffer, 0, len, StandardCharsets.UTF_8).trim();
                // System.out.println("收到Socket数据: " + rawData);

                String[] parts = rawData.split("\\|", 3);

                if (parts.length >= 2) {
                    String receivedId = parts[0];
                    String type = parts[1];

                    // 【关键修改】一旦收到有效数据，注册连接
                    if (currentServerId == null) {
                        currentServerId = receivedId;
                        activeConnections.put(receivedId, socket);
                        // System.out.println("连接已注册: " + receivedId);
                    }

                    if ("CMD_RESPONSE".equalsIgnoreCase(type)) {
                        String logs = (parts.length == 3) ? parts[2] : "(无输出)";
                        var future = SendLike.pendingCommandResponses.get(receivedId);
                        if (future != null) {
                            future.complete(logs);
                            System.out.printf("[%s] 收到指令反馈日志，长度: %d\n", receivedId, logs.length());
                        }
                    }
                    else if ("HEARTBEAT".equalsIgnoreCase(type)) {
                        // 心跳包：保持连接活跃，不做任何处理
                    }
                    else {
                        // ONLINE / OFFLINE
                        SInfo serverInfo = serverMap.get(receivedId);

                        if (serverInfo != null) {
                            ServerState state = "ONLINE".equalsIgnoreCase(type) ? ServerState.ONLINE : ServerState.OFFLINE;

                            // 只有状态确实改变或者是上线通知才打印/推送，避免重复刷屏
                            // (这里保留你原有的逻辑)
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
            }
        } catch (Exception e) {
            // 客户端断开连接通常会抛出异常，属正常现象
            // System.err.println("Socket 连接断开: " + e.getMessage());
        } finally {
            // 清理连接
            if (currentServerId != null) {
                activeConnections.remove(currentServerId);
                System.out.println("移除活跃连接: " + currentServerId);
            }
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