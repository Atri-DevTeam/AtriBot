package top.yzljc.qqbot.feature.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.botkits.image.AbstractImage;
import top.yzljc.qqbot.botkits.message.MessageSender;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通过 MC Server List Ping 获取服务器 MOTD，供 /motd 指令使用。
 */
public class Motd {

    private static final Logger log = LoggerFactory.getLogger(Motd.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final int PING_TIMEOUT_MS = 5000;
    private static final int PROTOCOL_VERSION = -1;
    private static final int DEFAULT_PORT = 25565;

    private static class HostPort {
        final String host;
        final int port;

        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }

        String display() {
            return port == DEFAULT_PORT ? host : host + ":" + port;
        }
    }

    private static class MotdResult {
        final String motd;
        final String version;
        final int online;
        final int max;
        final BufferedImage icon;

        MotdResult(String motd, String version, int online, int max, BufferedImage icon) {
            this.motd = motd != null ? motd : "";
            this.version = version != null ? version : "";
            this.online = online;
            this.max = max;
            this.icon = icon;
        }
    }

    public static void processCommand(long groupId, String rawMessage) {
        String trimmed = rawMessage.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String arg = parts.length >= 2 ? parts[1].trim() : null;

        if (arg == null || arg.isEmpty()) {
            MessageSender.sendGroupMessage(groupId, "用法: /motd <ip>", null);
            return;
        }

        HostPort hp = parseHostPort(arg);
        if (hp == null) {
            MessageSender.sendGroupMessage(groupId, "[MOTD] 无效地址，请使用 主机 或 主机:端口，如 mc.hypixel.net 或 mc.xxx.com:12345", null);
            return;
        }

        Executors.newSingleThreadExecutor().submit(() -> fetchAndSendMotd(groupId, hp));
    }

    /**
     * 解析 "mc.hypixel.net" 或 "mc.xxx.com:12345"。无端口则默认 25565。
     */
    private static HostPort parseHostPort(String input) {
        if (input == null || input.isBlank()) return null;
        input = input.trim();
        int idx = input.lastIndexOf(':');
        String host;
        int port = DEFAULT_PORT;
        if (idx >= 0) {
            host = input.substring(0, idx).trim();
            String portStr = input.substring(idx + 1).trim();
            if (host.isEmpty() || portStr.isEmpty()) return null;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                return null;
            }
            if (port < 1 || port > 65535) return null;
        } else {
            host = input;
        }
        if (host.isEmpty()) return null;
        return new HostPort(host, port);
    }

    private static void fetchAndSendMotd(long groupId, HostPort hp) {
        MotdResult result = fetchMotdData(hp.host, hp.port);
        File tmpDir = new File("tmp");
        if (!tmpDir.exists()) tmpDir.mkdirs();
        File tmpFile = new File(tmpDir, "motd_" + System.currentTimeMillis() + ".png");

        try {
            String display = hp.display();
            if (result == null) {
                MotdImageGen.generateFailure(display, hp.host, hp.port, tmpFile);
            } else {
                MotdImageGen.generate(display, hp.host, hp.port, result, tmpFile);
            }
            if (tmpFile.exists()) {
                byte[] imgBytes = Files.readAllBytes(tmpFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                MessageSender.sendGroupMessage(groupId, null, base64Img);
                log.info("MOTD 图片已发送 -> 群: {}, 地址: {}:{}", groupId, hp.host, hp.port);
            }
        } catch (Exception e) {
            log.error("MOTD 图片生成或发送异常: {}", e.getMessage());
            MessageSender.sendGroupMessage(groupId, "[MOTD] 图片生成失败: " + e.getMessage(), null);
        } finally {
            if (tmpFile.exists()) tmpFile.delete();
        }
    }

    /**
     * 向 MC 服务器发起 Server List Ping，解析 MOTD、版本、在线人数、favicon 等，供绘图使用。
     */
    private static MotdResult fetchMotdData(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PING_TIMEOUT_MS);
            socket.setSoTimeout(PING_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            sendHandshake(out, host, port);
            sendStatusRequest(out);
            sendPingRequest(out);

            String json = readStatusResponse(in);
            if (json == null) return null;

            JsonNode root = jsonMapper.readTree(json);
            String motd = extractDescription(root.path("description"));
            String version = root.path("version").path("name").asText("");
            int online = root.path("players").path("online").asInt(-1);
            int max = root.path("players").path("max").asInt(-1);
            BufferedImage icon = parseFavicon(root.path("favicon"));

            return new MotdResult(motd, version, online, max, icon);
        } catch (Exception e) {
            log.debug("MOTD ping 失败 {}:{} — {}", host, port, e.getMessage());
            return null;
        }
    }

    private static BufferedImage parseFavicon(JsonNode favicon) {
        if (favicon.isMissingNode() || !favicon.isTextual()) return null;
        String raw = favicon.asText("");
        if (!raw.contains("base64,")) return null;
        try {
            String b64 = raw.split("base64,", 2)[1].trim();
            byte[] bytes = Base64.getDecoder().decode(b64);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.debug("favicon 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static final class MotdImageGen extends AbstractImage {

        private static final int CARD_W = 640;
        private static final int CARD_H = 320;
        private static final int PAD = 24;
        private static final int ICON_SIZE = 64;
        private static final int LINE_H = 28;
        private static final int MAX_MOTD_CHARS = 48;

        static void generate(String serverName, String ip, int port, MotdResult data, File outFile) throws Exception {
            MotdImageGen gen = new MotdImageGen();
            gen.drawCard(serverName, ip, port, data, false, outFile);
        }

        static void generateFailure(String serverName, String ip, int port, File outFile) throws Exception {
            MotdImageGen gen = new MotdImageGen();
            gen.drawCard(serverName, ip, port, null, true, outFile);
        }

        private void drawCard(String serverName, String ip, int port, MotdResult data, boolean failed, File outFile) throws Exception {
            initBlank(CARD_W, CARD_H);
            Font baseFont = loadFont(Font.PLAIN, 1f);
            Font titleFont = baseFont.deriveFont(Font.BOLD, 22f);
            Font labelFont = baseFont.deriveFont(Font.PLAIN, 16f);
            Font valueFont = baseFont.deriveFont(Font.PLAIN, 15f);

            int y = PAD;
            g.setFont(titleFont);
            drawShadowText("[MOTD] " + serverName, PAD, y, Color.WHITE, Color.BLACK);
            y += LINE_H;

            g.setFont(labelFont);
            g.setColor(new Color(180, 180, 180));
            g.drawString(ip + ":" + port, PAD, y);
            y += LINE_H + 8;

            if (failed) {
                g.setFont(valueFont);
                g.setColor(new Color(255, 120, 120));
                g.drawString("请求超时或连接失败", PAD, y);
            } else {
                if (data.icon != null) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(data.icon, CARD_W - PAD - ICON_SIZE, PAD + 2, ICON_SIZE, ICON_SIZE, null);
                }

                g.setFont(valueFont);
                g.setColor(Color.WHITE);
                String motd = data.motd.isEmpty() ? "(无)" : truncate(data.motd, MAX_MOTD_CHARS);
                g.drawString("描述: " + motd, PAD, y);
                y += LINE_H;

                if (!data.version.isEmpty()) {
                    g.drawString("版本: " + data.version, PAD, y);
                    y += LINE_H;
                }
                if (data.online >= 0 && data.max >= 0) {
                    g.drawString("玩家: " + data.online + " / " + data.max, PAD, y);
                }
            }

            g.setFont(baseFont.deriveFont(Font.PLAIN, 12f));
            String timeStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            drawCenteredShadowText(timeStr, CARD_H - 16, Color.GRAY, Color.BLACK);
            saveAndDispose(outFile);
        }

        private static String truncate(String s, int max) {
            if (s == null || s.length() <= max) return s;
            return s.substring(0, max - 1) + "…";
        }
    }

    private static String extractDescription(JsonNode desc) {
        if (desc.isMissingNode() || desc.isNull()) return "";
        if (desc.isTextual()) return stripFormatting(desc.asText());
        if (desc.isObject()) {
            if (desc.has("text")) return stripFormatting(desc.get("text").asText(""));
            if (desc.has("extra") && desc.get("extra").isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode n : desc.get("extra")) {
                    if (n.has("text")) sb.append(n.get("text").asText(""));
                    else if (n.isTextual()) sb.append(n.asText());
                }
                return stripFormatting(sb.toString());
            }
        }
        return stripFormatting(desc.asText(""));
    }

    private static String stripFormatting(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "").trim();
    }

    private static void sendHandshake(OutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream ds = new DataOutputStream(buf);
        writeVarInt(ds, PROTOCOL_VERSION);
        writeString(ds, host);
        ds.writeShort(port & 0xFFFF);
        writeVarInt(ds, 1); // next state: status
        byte[] payload = buf.toByteArray();
        writePacket(out, 0x00, payload);
    }

    private static void sendStatusRequest(OutputStream out) throws IOException {
        writePacket(out, 0x00, new byte[0]);
    }

    private static void sendPingRequest(OutputStream out) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream ds = new DataOutputStream(buf);
        ds.writeLong(System.currentTimeMillis());
        writePacket(out, 0x01, buf.toByteArray());
    }

    private static void writePacket(OutputStream out, int packetId, byte[] payload) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream ds = new DataOutputStream(buf);
        writeVarInt(ds, packetId);
        ds.write(payload);
        byte[] data = buf.toByteArray();
        buf.reset();
        DataOutputStream ds2 = new DataOutputStream(buf);
        writeVarInt(ds2, data.length);
        ds2.write(data);
        out.write(buf.toByteArray());
        out.flush();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, b.length);
        out.write(b);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int v = 0, shift = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new EOFException("VarInt EOF");
            v |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
            if (shift >= 35) throw new IOException("VarInt too long");
        }
    }

    private static String readString(InputStream in) throws IOException {
        int len = readVarInt(in);
        if (len <= 0 || len > 0x7FFFF) throw new IOException("Invalid string length: " + len);
        byte[] b = new byte[len];
        int n = 0;
        while (n < len) {
            int r = in.read(b, n, len - n);
            if (r <= 0) throw new EOFException("String EOF");
            n += r;
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String readStatusResponse(InputStream in) throws IOException {
        int packetLen = readVarInt(in);
        if (packetLen <= 0 || packetLen > 0x1FFFF) return null;
        byte[] packet = new byte[packetLen];
        int n = 0;
        while (n < packetLen) {
            int r = in.read(packet, n, packetLen - n);
            if (r <= 0) return null;
            n += r;
        }
        DataInputStream ds = new DataInputStream(new ByteArrayInputStream(packet));
        int id = readVarInt(ds);
        if (id != 0) return null;
        return readString(ds);
    }
}
