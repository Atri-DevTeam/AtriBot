package top.yzljc.qqbot.socket;

import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.message.SensitiveWordFilter;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.feature.minecraft.ServerRcon;
import top.yzljc.qqbot.feature.minecraft.ServerStatus;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketManager {

    private static final Logger log = LoggerFactory.getLogger(SocketManager.class);
    static Settings settings = Config.getInstance();
    private static final long debugGroupId = settings.getDebugGroupId();
    private static final String LIST_FILE = "serverlist.txt";
    private static final Map<String, ServerInfo> serverMap = new HashMap<>();
    private static final Map<String, Socket> activeConnections = new ConcurrentHashMap<>();

    private static final Pattern STRICT_FILTER_PATTERN = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fa5]");

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
            log.warn("发送失败，目标服务器未连接：{}", serverId);
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
            log.error("Socket 发送异常：{}", e.getMessage());
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
                    log.warn("格式错误跳过：{}", item);
                    continue;
                }
                try {
                    long gid = Long.parseLong(fs[0].trim());
                    String name = fs[1].trim();
                    String ip = fs[2].trim();
                    int port = Integer.parseInt(fs[3].trim());
                    String id = fs[4].trim();

                    serverMap.put(id, new ServerInfo(gid, name, ip, port, id));
                    log.info(" -> 加载配置：[{}] {} (群：{})", id, name, gid);
                } catch (Exception e) {
                    log.error("解析错误：{}", item);
                }
            }
            if (serverMap.isEmpty()) {
                log.warn("未读取到服务器配置，请检查 serverlist.txt");
                log.warn("格式：群号/名称/IP/端口/编号#……");
            } else {
                log.info("已加载 {} 个服务器配置", serverMap.size());
            }
        } catch (Exception e) {
            log.error("读取配置文件失败：{}", e.getMessage());
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
                log.info("正在监听Socket端口：{}，等待插件连接……", port);

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

                // 第一层拆包：ID | Type | Content
                // split limit 3 确保 Content 内部的 | 不会被切分
                String[] parts = rawData.split("\\|", 3);

                if (parts.length >= 2) {
                    String receivedId = parts[0];
                    String type = parts[1];
                    String content = parts.length == 3 ? parts[2] : "";

                    // 注册连接
                    if (currentServerId == null) {
                        currentServerId = receivedId;
                        activeConnections.put(receivedId, socket);
                    }

                    ServerInfo info = serverMap.get(receivedId);
                    String serverName = (info != null) ? info.name : receivedId;


                    if ("CMD_RESPONSE".equalsIgnoreCase(type)) {
                        String logs = content.isEmpty() ? "(无输出)" : content;
                        var future = ServerRcon.pendingCommandResponses.get(receivedId);
                        if (future != null) {
                            future.complete(logs);
                            log.info("[{}] 收到指令反馈日志，长度：{}", receivedId, logs.length());
                        }

                    } else if ("HEARTBEAT".equalsIgnoreCase(type)) {

                    } else if ("Player".equalsIgnoreCase(type)) {
                        String[] details = content.split("\\|", 2);
                        if (details.length == 2) {
                            String action = details[0];
                            String playerName = details[1];
                            String msg = "";

                            if ("JOIN".equalsIgnoreCase(action)) {
                                msg = String.format("[%s] 玩家 %s 加入了服务器", serverName, playerName);
                                System.out.println(msg);
                            } else if ("QUIT".equalsIgnoreCase(action)) {
                                msg = String.format("[%s] 玩家 %s 离开了服务器", serverName, playerName);
                               log.info(msg); 
                            }

                            if (!msg.isEmpty()) {
                                sendToGroup(debugGroupId, msg);
                            }
                        }

                    } else if ("Chat".equalsIgnoreCase(type)) {
                        String[] details = content.split("\\|", 2);
                        if (details.length == 2) {
                            String playerName = details[0];
                            String chatMsg = details[1];

                            boolean isDirty = SensitiveWordFilter.containsSensitiveWord(chatMsg);

                            if (!isDirty) {
                                String cleanedMsg = STRICT_FILTER_PATTERN.matcher(chatMsg).replaceAll("");
                                isDirty = SensitiveWordFilter.containsSensitiveWord(cleanedMsg);
                            }

                            if (isDirty) {
                                log.info("检测到违规消息，拦截到服务器 {} 玩家 {} 的消息： {}", serverName, playerName, chatMsg);
                                sendToGroup(debugGroupId, "有违规聊天内容已进行拦截，请管理员进行审查！");
                                continue;
                            }

                            String formattedMsg = String.format("[%s] %s: %s", serverName, playerName, chatMsg);
                            log.info("转发聊天：{}", formattedMsg);

                            sendToGroup(debugGroupId, formattedMsg);
                        }

                    } else if ("ONLINE".equalsIgnoreCase(type) || "OFFLINE".equalsIgnoreCase(type)) {

                        if (info != null) {
                            boolean isOnline = "ONLINE".equalsIgnoreCase(type);
                            if (isOnline) {
                                log.info("[{}] 服务器上线，准备进行推送……", info.name);
                            }

                            ServerStatus.sendReport(
                                    info.groupId,
                                    info.name,
                                    info.ip,
                                    info.port,
                                    info.id,
                                    isOnline
                            );
                            log.info("[{}] 状态已处理: {}", info.name, type);
                        } else {
                            log.warn("收到未知服务器ID的数据：{}", receivedId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Socket连接异常：{}", e.getMessage());
        } finally {
            if (currentServerId != null) {
                activeConnections.remove(currentServerId);
                log.info("移除活跃连接：{}", currentServerId);
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void sendToGroup(long groupId, String message) {
        MessageSender.sendGroupMessage(groupId,message);
        log.info("Minecraft服务器消息转发成功，目标群号 {}，目标消息内容：{}", groupId, message);
    }
}
