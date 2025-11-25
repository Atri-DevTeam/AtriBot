package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * MC服务器探测 (多群监听版 + 颜色代码清洗)
 * 支持针对不同服务器推送到不同群聊
 */
public class App {
    // napcat 接口
    private static final String NAPCAT_API = "http://106.14.23.232:8848/send_group_msg";

    private static final String LIST_FILE = "serverlist.txt";
    private static final int CHECK_INTERVAL_SEC = 10;

    // 预编译正则，用于去除颜色代码
    // 1. (?i)[§&][0-9a-fk-or] : 匹配 §c, &c, §1, &r 等 legacy 格式 (忽略大小写)
    // 2. <[^>]+> : 匹配 <red>, <bold>, <#ffffff> 等 MiniMessage/XML 格式
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)[§&][0-9a-fk-or]|<[^>]+>");

    // 定义服务器状态枚举
    private enum ServerState {
        OFFLINE("离线"),
        ONLINE("在线"),
        MAINTENANCE("维护");

        final String desc;
        ServerState(String desc) { this.desc = desc; }
    }

    private static class PingResult {
        ServerState state;
        String version;
        PingResult(ServerState state, String version) {
            this.state = state;
            this.version = version;
        }
    }

    private static class SInfo {
        long groupId; // 群号
        String name, ip;
        int port;
        ServerState lastState = ServerState.OFFLINE;
        boolean firstCheck = true;

        SInfo(long g, String n, String i, int p) {
            groupId = g;
            name = n;
            ip = i;
            port = p;
        }
    }

    public static void main(String[] args) {
        System.out.println("==== Minecraft Server Monitor ====");
        List<SInfo> servers = loadServers();
        if (servers.isEmpty()) {
            System.err.println("未读取到服务器，请填写 serverlist.txt");
            System.err.println("格式: 群号/名称/IP/端口#群号2/名称2/IP2/端口2");
            return;
        }
        System.out.printf("已加载%d个监听项\n", servers.size());
        servers.forEach(s -> System.out.printf("  [群:%d] %s (%s:%d)\n", s.groupId, s.name, s.ip, s.port));

        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> runChecks(servers), 0, CHECK_INTERVAL_SEC, TimeUnit.SECONDS);

        try { Thread.currentThread().join(); } catch (Exception ignored) {}
    }

    private static List<SInfo> loadServers() {
        List<SInfo> list = new ArrayList<>();
        // 使用 UTF-8 读取文件以支持中文
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(LIST_FILE), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null || line.isEmpty()) return list;

            // 移除可能的 BOM 头
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }

            String[] items = line.split("#");
            for (String item : items) {
                String[] fs = item.split("/");
                if (fs.length != 4) {
                    System.err.println("跳过格式错误条目: " + item);
                    continue;
                }
                try {
                    long gid = Long.parseLong(fs[0].trim());
                    String name = fs[1].trim();
                    String ip = fs[2].trim();
                    int port = Integer.parseInt(fs[3].trim());
                    list.add(new SInfo(gid, name, ip, port));
                } catch (NumberFormatException e) {
                    System.err.println("数字解析错误: " + item);
                }
            }
        } catch (Exception e) {
            System.err.println("读取配置异常: " + e.getMessage());
        }
        return list;
    }

    private static void runChecks(List<SInfo> servers) {
        for (var s : servers) {
            ServerState prevState = s.lastState;

            // 探测获取状态
            PingResult result = pingMinecraftServer(s.ip, s.port);
            ServerState nowState = result.state;

            // 状态变更且非首次检查 -> 推送
            if (nowState != prevState && !s.firstCheck) {
                boolean ok = sendToQQBot(s, nowState);
                String info = ok ? "-> 推送成功√" : "-> 推送失败×";
                System.out.printf("[群:%d|%s] 变更推送: %s -> %s %s\n", s.groupId, s.name, prevState.desc, nowState.desc, info);
            }

            // 控制台日志
            if (nowState != prevState) {
                System.out.printf("[群:%d|%s] 状态变化: %s -> %s (Version: %s)\n", s.groupId, s.name, prevState.desc, nowState.desc, result.version);
            }

            s.lastState = nowState;
            s.firstCheck = false;
        }
    }

    /**
     * 构造图文消息并推送 (5秒延迟)
     */
    private static boolean sendToQQBot(SInfo server, ServerState state) {
        try {
            System.out.printf("[群:%d|%s] 状态变更，等待5秒以生成图片...\n", server.groupId, server.name);
            Thread.sleep(5000);

            ObjectMapper objectMapper = new ObjectMapper();

            // 1. 构造纯文本消息节点
            String textContent = String.format(
                    "[!] 服务器状态更新\n服务器：%s\n地址：%s:%d\n状态：%s",
                    server.name, server.ip, server.port, state.desc
            );

            Map<String, Object> textData = new HashMap<>();
            textData.put("text", textContent);

            Map<String, Object> textNode = new HashMap<>();
            textNode.put("type", "text");
            textNode.put("data", textData);

            // 2. 构造图片消息节点 (mcstatus API)
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

            // 4. 构建最终 Payload
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", server.groupId);
            payloadMap.put("message", messageList);

            String payload = objectMapper.writeValueAsString(payloadMap);

            // 5. 发送请求
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

    /**
     * 执行 Minecraft 协议握手
     */
    private static PingResult pingMinecraftServer(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(5000);
            socket.connect(new InetSocketAddress(ip, port), 3000);

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            DataInputStream dis = new DataInputStream(in);

            // Handshake
            ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
            DataOutputStream handshakeDos = new DataOutputStream(handshakeBytes);
            writeVarInt(handshakeDos, 0x00);
            writeVarInt(handshakeDos, -1);
            writeVarInt(handshakeDos, ip.length());
            handshakeDos.writeBytes(ip);
            handshakeDos.writeShort(port);
            writeVarInt(handshakeDos, 1);

            writeVarInt(dos, handshakeBytes.size());
            dos.write(handshakeBytes.toByteArray());

            // Request
            dos.writeByte(0x01);
            dos.writeByte(0x00);

            // Response
            int totalLength = readVarInt(dis);
            int packetId = readVarInt(dis);

            if (packetId != 0x00) throw new IOException("Invalid packet ID");

            int jsonLength = readVarInt(dis);
            byte[] jsonBytes = new byte[jsonLength];
            dis.readFully(jsonBytes);
            String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonStr);

            // 获取原始版本字符串
            String rawVersion = root.path("version").path("name").asText("Unknown");

            // 【关键修改】在此处清理颜色代码
            String cleanVersion = stripFormatting(rawVersion);

            // 判断维护 (现在可以忽略颜色代码正确判断了，比如 §cMaintenance 也能被识别)
            if ("Maintenance".equalsIgnoreCase(cleanVersion)) {
                return new PingResult(ServerState.MAINTENANCE, cleanVersion);
            } else {
                return new PingResult(ServerState.ONLINE, cleanVersion);
            }

        } catch (Exception e) {
            return new PingResult(ServerState.OFFLINE, "N/A");
        }
    }

    /**
     * 去除 MC 样式代码和 HTML/XML 风格标签
     */
    private static String stripFormatting(String input) {
        if (input == null) return "";
        // 将所有匹配到的颜色代码替换为空字符串
        return STRIP_COLOR_PATTERN.matcher(input).replaceAll("").trim();
    }

    private static void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
        while (true) {
            if ((paramInt & 0xFFFFFF80) == 0) {
                out.writeByte(paramInt);
                return;
            }
            out.writeByte(paramInt & 0x7F | 0x80);
            paramInt >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int i = 0;
        int j = 0;
        while (true) {
            int k = in.readByte();
            i |= (k & 0x7F) << j++ * 7;
            if (j > 5) throw new RuntimeException("VarInt too big");
            if ((k & 0x80) != 128) break;
        }
        return i;
    }
}