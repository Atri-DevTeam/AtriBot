package top.yzljc.qqbot.socket;

import top.yzljc.qqbot.minecraft.SendCommand;
import top.yzljc.qqbot.minecraft.StatusReporter;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketManager {
    private static final String LIST_FILE = "serverlist.txt";

    // 服务器配置缓存 Map<ServerID, Info>
    private static final Map<String, ServerInfo> serverMap = new HashMap<>();
    // 活跃连接 Map<ServerID, Socket>
    private static final Map<String, Socket> activeConnections = new ConcurrentHashMap<>();

    // 内部数据结构
    static class ServerInfo {
        long groupId;
        String name;
        String ip;
        int port;
        String id;

        ServerInfo(long g, String n, String i, int p, String id) {
            groupId = g;
            name = n;
            ip = i;
            port = p;
            this.id = id;
        }
    }

    /**
     * 发送指令到指定服务器
     */
    public static boolean sendCommand(String serverId, String command, String secret) {
        Socket client = activeConnections.get(serverId);
        if (client == null || client.isClosed()) {
            System.err.println("[SocketManager] 发送失败，目标服务器未连接: " + serverId);
            return false;
        }
        try {
            // 协议格式: EXEC_CMD|指令内容|密钥
            String payload = "EXEC_CMD|" + command + "|" + secret;
            OutputStream out = client.getOutputStream();
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            System.err.println("[SocketManager] Socket 发送异常: " + e.getMessage());
            activeConnections.remove(serverId);
            return false;
        }
    }

    /**
     * 加载服务器配置文件
     */
    public static void loadConfig() {
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

                    serverMap.put(id, new ServerInfo(gid, name, ip, port, id));
                    System.out.printf("  -> 加载配置: [%s] %s (群:%d)\n", id, name, gid);
                } catch (Exception e) {
                    System.err.println("解析错误: " + item);
                }
            }
            if (serverMap.isEmpty()) {
                System.err.println("未读取到服务器配置，请检查 serverlist.txt");
                System.err.println("格式: 群号/名称/IP/端口/编号#...");
            } else {
                System.out.printf("已加载 %d 个服务器配置。\n", serverMap.size());
            }
        } catch (Exception e) {
            System.err.println("读取配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 启动 Socket 服务端
     */
    public static void start(int port) {
        // 使用新线程启动监听，避免阻塞主线程
        new Thread(() -> {
            ExecutorService threadPool = Executors.newCachedThreadPool();
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("正在监听Socket端口: " + port + "，等待插件连接...");

                while (true) {
                    Socket client = serverSocket.accept();
                    threadPool.submit(() -> handleClient(client));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "SocketServer-Thread").start();
    }

    private static void handleClient(Socket socket) {
        String currentServerId = null;

        try (InputStream in = socket.getInputStream()) {
            byte[] buffer = new byte[8192];
            int len;

            while ((len = in.read(buffer)) != -1) {
                String rawData = new String(buffer, 0, len, StandardCharsets.UTF_8).trim();

                String[] parts = rawData.split("\\|", 3);

                if (parts.length >= 2) {
                    String receivedId = parts[0];
                    String type = parts[1];

                    if (currentServerId == null) {
                        currentServerId = receivedId;
                        activeConnections.put(receivedId, socket);
                    }

                    if ("CMD_RESPONSE".equalsIgnoreCase(type)) {
                        String logs = (parts.length == 3) ? parts[2] : "(无输出)";
                        // 引用 SendCommand 中的 pending map
                        var future = SendCommand.pendingCommandResponses.get(receivedId);
                        if (future != null) {
                            future.complete(logs);
                            System.out.printf("[%s] 收到指令反馈日志，长度: %d\n", receivedId, logs.length());
                        }
                    } else if ("HEARTBEAT".equalsIgnoreCase(type)) {
                        // Keep alive
                    } else {
                        // 处理服务器状态上报 (ONLINE / OFFLINE)
                        ServerInfo serverInfo = serverMap.get(receivedId);

                        if (serverInfo != null) {
                            boolean isOnline = "ONLINE".equalsIgnoreCase(type);

                            if (isOnline) {
                                System.out.printf("[%s] 服务器上线，准备进行推送...\n", serverInfo.name);
                            }

                            // 调用 StatusReporter 进行推送
                            StatusReporter.sendReport(
                                    serverInfo.groupId,
                                    serverInfo.name,
                                    serverInfo.ip,
                                    serverInfo.port,
                                    serverInfo.id,
                                    isOnline
                            );

                            System.out.printf("[%s] 状态已处理: %s\n", serverInfo.name, type);
                        } else {
                            System.err.println("收到未知服务器ID的数据: " + receivedId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore disconnects
        } finally {
            if (currentServerId != null) {
                activeConnections.remove(currentServerId);
                System.out.println("移除活跃连接: " + currentServerId);
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}