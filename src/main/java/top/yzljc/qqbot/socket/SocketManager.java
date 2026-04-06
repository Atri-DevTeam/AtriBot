package top.yzljc.qqbot.socket;

import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.config.LoadIllegalWords;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.feature.minecraft.ServerRcon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
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
    private static final String LIST_FILE = ConfigFile.SERVER_LIST.getFileName();
    private static final Map<String, ServerInfo> serverMap = new HashMap<>();
    private static final Map<String, Socket> activeConnections = new ConcurrentHashMap<>();
    private static final ExecutorService SOCKET_THREAD_POOL = Executors.newFixedThreadPool(20);

    private static final Pattern STRICT_FILTER_PATTERN = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fa5]");

    public static class ServerInfo {
        public long group_id;
        public String name;
        public String ip;
        public int port;
        public String id;
        public String server_mode;
    }

    public static void loadConfig() {
        serverMap.clear();
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<ServerInfo> list = mapper.readValue(new File(LIST_FILE), new TypeReference<List<ServerInfo>>() {});
            for (ServerInfo s : list) {
                serverMap.put(s.id, s);
                log.info(" -> 加载配置：[{}] {} (群：{})", s.id, s.name, s.group_id);
            }
            if (serverMap.isEmpty()) {
                log.warn("未读取到服务器配置，请检查 serverlist.json");
            } else {
                log.info("已加载 {} 个服务器配置", serverMap.size());
            }
        } catch (Exception e) {
            log.error("读取配置文件失败：{}", e.getMessage());
        }
    }

    public static boolean sendCommand(String serverId, String command, String secret) {
        Socket client = activeConnections.get(serverId);
        if (client == null || client.isClosed()) {
            log.warn("发送失败，目标服务器未连接：{}", serverId);
            return false;
        }
        try {
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

    public static void start(int port) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log.info("正在监听Socket端口：{}，等待插件连接……", port);

                while (true) {
                    Socket client = serverSocket.accept();
                    SOCKET_THREAD_POOL.submit(() -> handleClient(client));
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
                    String content = parts.length == 3 ? parts[2] : "";

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
                                msg = String.format("玩家 %s 加入了服务器", playerName);
                                log.info(msg);
                            } else if ("QUIT".equalsIgnoreCase(action)) {
                                msg = String.format("玩家 %s 离开了服务器", playerName);
                                log.info(msg);
                            }

                            if (!msg.isEmpty()) {
                                sendToGroup(msg);
                            }
                        }

                    } else if ("Chat".equalsIgnoreCase(type)) {
                        String[] details = content.split("\\|", 2);
                        if (details.length == 2) {
                            String playerName = details[0];
                            String chatMsg = details[1];

                            boolean isDirty = LoadIllegalWords.containsSensitiveWord(chatMsg);

                            if (!isDirty) {
                                String cleanedMsg = STRICT_FILTER_PATTERN.matcher(chatMsg).replaceAll("");
                                isDirty = LoadIllegalWords.containsSensitiveWord(cleanedMsg);
                            }

                            if (isDirty) {
                                log.info("检测到违规消息，拦截到服务器 {} 玩家 {} 的消息： {}", serverName, playerName, chatMsg);
                                sendToGroup("有违规聊天内容已进行拦截，请管理员进行审查！");
                                continue;
                            }

                            String formattedMsg = String.format("%s: %s", playerName, chatMsg);
                            log.info("转发聊天：{}", formattedMsg);

                            sendToGroup(formattedMsg);
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

    private static void sendToGroup(String message) {
        MessageSender.sendGroupMessage(SocketManager.debugGroupId,message);
        log.info("Minecraft服务器消息转发成功，目标群号 {}，目标消息内容：{}", SocketManager.debugGroupId, message);
    }
}