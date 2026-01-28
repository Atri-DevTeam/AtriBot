package top.yzljc.qqbot.feature.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.botkits.image.AbstractImage;
import top.yzljc.qqbot.botkits.message.MessageSender;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Motd {

    private static final Logger log = LoggerFactory.getLogger(Motd.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final Map<Character, Color> MC_COLORS = new HashMap<>();
    static {
        MC_COLORS.put('0', new Color(0, 0, 0));
        MC_COLORS.put('1', new Color(0, 0, 170));
        MC_COLORS.put('2', new Color(0, 170, 0));
        MC_COLORS.put('3', new Color(0, 170, 170));
        MC_COLORS.put('4', new Color(170, 0, 0));
        MC_COLORS.put('5', new Color(170, 0, 170));
        MC_COLORS.put('6', new Color(255, 170, 0));
        MC_COLORS.put('7', new Color(170, 170, 170));
        MC_COLORS.put('8', new Color(85, 85, 85));
        MC_COLORS.put('9', new Color(85, 85, 255));
        MC_COLORS.put('a', new Color(85, 255, 85));
        MC_COLORS.put('b', new Color(85, 255, 255));
        MC_COLORS.put('c', new Color(255, 85, 85));
        MC_COLORS.put('d', new Color(255, 85, 255));
        MC_COLORS.put('e', new Color(255, 255, 85));
        MC_COLORS.put('f', new Color(255, 255, 255));
    }
    private static final String API_BASE = "https://api.mcstatus.io/v2/status/java/";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int DEFAULT_PORT = 25565;

    private record HostPort(String host, int port) {}

    private record MotdResult(String motd, List<String> motdRawLines, String version, int online, int max,
                              BufferedImage icon) {
            private MotdResult(String motd, List<String> motdRawLines, String version, int online, int max, BufferedImage icon) {
                this.motd = motd != null ? motd : "";
                this.motdRawLines = motdRawLines != null ? motdRawLines : List.of();
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
            MessageSender.sendGroupMessage(groupId, "无效地址，请使用 主机 或 主机:端口，如 mc.hypixel.net 或 mc.xxx.com:12345", null);
            return;
        }

        Executors.newSingleThreadExecutor().submit(() -> fetchAndSendMotd(groupId, hp));
    }

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
            if (result == null) {
                MotdImageGen.generateFailure(hp.host, hp.port, tmpFile);
            } else {
                MotdImageGen.generate(hp.host, hp.port, result, tmpFile);
            }
            if (tmpFile.exists()) {
                byte[] imgBytes = Files.readAllBytes(tmpFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                MessageSender.sendGroupMessage(groupId, null, base64Img);
                log.info("MOTD 图片已发送 -> 群: {}, 地址: {}:{}", groupId, hp.host, hp.port);
            }
        } catch (Exception e) {
            log.error("MOTD 图片生成或发送异常: {}", e.getMessage());
            MessageSender.sendGroupMessage(groupId, "MOTD 图片生成失败: " + e.getMessage(), null);
        } finally {
            if (tmpFile.exists()) tmpFile.delete();
        }
    }

    private static MotdResult fetchMotdData(String host, int port) {
        String ipPort = host + ":" + port;
        String url = API_BASE + ipPort;
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
                log.debug("MOTD API 非 200: {} — {}", url, conn.getResponseCode());
                return null;
            }
            String json;
            try (InputStream in = conn.getInputStream()) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonNode root = jsonMapper.readTree(json);
            if (!root.path("online").asBoolean(true)) {
                log.debug("MOTD API 返回 offline: {}", ipPort);
                return null;
            }
            String motd = parseMotdFromApi(root.path("motd"));
            List<String> motdRawLines = parseMotdRawLinesFromApi(root.path("motd"));
            String version = parseVersionFromApi(root.path("version"));
            int online = root.path("players").path("online").asInt(0);
            int max = root.path("players").path("max").asInt(0);
            BufferedImage icon = parseIconFromApi(root.path("icon"));
            return new MotdResult(motd, motdRawLines, version, online, max, icon);
        } catch (Exception e) {
            log.debug("MOTD API 请求失败 {} — {}", ipPort, e.getMessage());
            return null;
        }
    }

    private static String parseMotdFromApi(JsonNode motd) {
        if (motd.isMissingNode() || !motd.isObject()) return "";
        JsonNode clean = motd.path("clean");
        if (!clean.isMissingNode() && clean.isTextual()) {
            return clean.asText("").replace("\\n", "\n").trim();
        }
        JsonNode raw = motd.path("raw");
        if (raw.isTextual()) {
            return stripFormatting(raw.asText("").replace("\\n", "\n")).trim();
        }
        if (raw.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < raw.size(); i++) {
                JsonNode n = raw.get(i);
                if (n.isTextual()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(stripFormatting(n.asText("")));
                }
            }
            return sb.toString().trim();
        }
        return "";
    }

    private static List<String> parseMotdRawLinesFromApi(JsonNode motd) {
        List<String> out = new ArrayList<>();
        if (motd.isMissingNode() || !motd.isObject()) return out;
        JsonNode raw = motd.path("raw");
        if (raw.isTextual()) {
            String s = raw.asText("").replace("\\n", "\n").trim();
            String[] lines = s.split("\n", 3);
            if (lines[0] != null && !lines[0].trim().isEmpty()) out.add(lines[0].trim());
            if (lines.length > 1 && lines[1] != null && !lines[1].trim().isEmpty()) out.add(lines[1].trim());
            return out;
        }
        if (raw.isArray()) {
            for (int i = 0; i < Math.min(2, raw.size()); i++) {
                JsonNode n = raw.get(i);
                if (n.isTextual()) {
                    String t = n.asText("").trim();
                    if (!t.isEmpty()) out.add(t);
                }
            }
        }
        return out;
    }

    private static String parseVersionFromApi(JsonNode ver) {
        if (ver.isMissingNode()) return "";
        String s;
        if (ver.isObject()) {
            s = ver.path("name_clean").asText("");
            if (s.isEmpty()) s = ver.path("name_raw").asText("");
        } else {
            s = ver.asText("");
        }
        return stripFormatting(s);
    }

    private static BufferedImage parseIconFromApi(JsonNode icon) {
        if (icon.isMissingNode() || !icon.isTextual()) return null;
        String raw = icon.asText("");
        if (!raw.contains("base64,")) return null;
        try {
            String b64 = raw.split("base64,", 2)[1].trim();
            byte[] bytes = Base64.getDecoder().decode(b64);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.debug("icon 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static String stripFormatting(String s) {
        if (s == null) return "";
        String t = s.replaceAll("§x[0-9a-fA-F]{6}", "")
            .replaceAll("§[0-9a-fk-or]", "")
            .replaceAll("&[0-9a-fk-or]", "")
            .replaceAll("&x[0-9a-fA-F]{6}", "");
        t = t.replace("§", "").replace("&", "");
        t = sanitizeMysterySymbols(t);
        return t.trim();
    }

    private static String sanitizeMysterySymbols(String s) {
        if (s == null) return "";
        return s.replace("Û", "").replace("ū", "");
    }

    private static List<TextSegment> parseLegacyColorCodes(String text) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null) return segments;
        Color currentColor = Color.WHITE;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char n = text.charAt(i + 1);
                if (n == 'x' && i + 14 <= text.length()) {
                    boolean hex = true;
                    for (int k = 0; k < 6; k++) {
                        int j = i + 2 + k * 2;
                        if (text.charAt(j) != '§' || j + 1 >= text.length()) { hex = false; break; }
                        char h = Character.toLowerCase(text.charAt(j + 1));
                        if ("0123456789abcdef".indexOf(h) < 0) { hex = false; break; }
                    }
                    if (hex) { i += 13; continue; }
                }
                if (!buffer.isEmpty()) {
                    segments.add(new TextSegment(buffer.toString(), currentColor));
                    buffer.setLength(0);
                }
                char code = Character.toLowerCase(n);
                if (code == 'r') currentColor = Color.WHITE;
                else if (MC_COLORS.containsKey(code)) currentColor = MC_COLORS.get(code);
                i++;
            } else if (c == '&' && i + 1 < text.length()) {
                if (!buffer.isEmpty()) {
                    segments.add(new TextSegment(buffer.toString(), currentColor));
                    buffer.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r') currentColor = Color.WHITE;
                else if (MC_COLORS.containsKey(code)) currentColor = MC_COLORS.get(code);
                i++;
            } else {
                buffer.append(c);
            }
        }
        if (!buffer.isEmpty()) {
            segments.add(new TextSegment(buffer.toString(), currentColor));
        }
        return segments;
    }

    private record TextSegment(String text, Color color) {
    }

    private static final class MotdImageGen extends AbstractImage {

        private static final int CARD_W = 640;
        private static final int CARD_H = 320;
        private static final int PAD = 24;
        private static final int ADDRESS_TOP_OFFSET = 12;
        private static final int ICON_SIZE = 56;
        private static final int OVERLAY_MARGIN = 32;
        private static final int MOTD_FONT_SIZE = 26;
        private static final int MOTD_FONT_SIZE_MIN = 12;
        private static final int MOTD_LINE_GAP = 12;
        private static final int INFO_FONT_SIZE = 14;
        private static final int INFO_FONT_SIZE_MIN = 10;

        static void generate(String ip, int port, MotdResult data, File outFile) throws Exception {
            MotdImageGen gen = new MotdImageGen();
            gen.drawCard(ip, port, data, false, outFile);
        }

        static void generateFailure(String ip, int port, File outFile) throws Exception {
            MotdImageGen gen = new MotdImageGen();
            gen.drawCard(ip, port, null, true, outFile);
        }

        private void drawCard(String ip, int port, MotdResult data, boolean failed, File outFile) throws Exception {
            try {
                initFromBackground("manoyinxi.png");
            } catch (Exception e) {
                initBlank(CARD_W, CARD_H);
            }
            Font baseFont = loadFont(Font.PLAIN, 1f);
            int panelLeft = OVERLAY_MARGIN;
            int panelTop = OVERLAY_MARGIN;
            int panelW = width - OVERLAY_MARGIN * 2;
            int panelH = height - OVERLAY_MARGIN * 2;

            g.setColor(new Color(30, 30, 30, 200));
            g.fillRoundRect(panelLeft, panelTop, panelW, panelH, 20, 20);

            int innerLeft = panelLeft + PAD;
            int innerRight = panelLeft + panelW - PAD;
            int y = panelTop + PAD + ADDRESS_TOP_OFFSET;
            String address = ip + ":" + port;
            int maxContentWidth = panelW - PAD * 2;

            Font titleFont = baseFont.deriveFont(Font.BOLD, 20f);
            g.setFont(titleFont);
            drawShadowText("地址：" + address, innerLeft, y, Color.WHITE, Color.BLACK);
            if (!failed && data != null && data.icon != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(data.icon, innerRight - ICON_SIZE, y - 18, ICON_SIZE, ICON_SIZE, null);
            }
            y += 28;

            int motdAreaTop = y;
            int motdAreaBottom = panelTop + panelH - 80;
            int centerY = (motdAreaTop + motdAreaBottom) / 2;
            g.setColor(Color.WHITE);

            if (failed) {
                Font motdFont = baseFont.deriveFont(Font.BOLD, (float) MOTD_FONT_SIZE);
                g.setFont(motdFont);
                drawCenteredShadowText("请求超时或连接失败", centerY, new Color(255, 120, 120), Color.BLACK);
            } else {
                List<List<TextSegment>> segLines = motdToSegmentLines(data);
                Font motdFont = baseFont.deriveFont(Font.BOLD, (float) MOTD_FONT_SIZE);
                float motdSize = scaleFontToFitSegments(g, motdFont, segLines, maxContentWidth);
                motdFont = baseFont.deriveFont(Font.BOLD, motdSize);
                g.setFont(motdFont);
                FontMetrics fm = g.getFontMetrics();
                int lineHeight = fm.getHeight() + MOTD_LINE_GAP;
                int startY = centerY - (segLines.size() * lineHeight - MOTD_LINE_GAP) / 2 + fm.getAscent();
                for (int i = 0; i < segLines.size(); i++) {
                    drawCenteredSegmentsWithShadow(motdFont, segLines.get(i), startY + i * lineHeight);
                }
            }

            y = panelTop + panelH - 52;
            String info = "";
            if (!failed && data != null) {
                if (!data.version.isEmpty()) info = "版本: " + data.version;
                if (data.online >= 0 && data.max >= 0) {
                    if (!info.isEmpty()) info += " | 玩家: " + data.online + " / " + data.max;
                    else info = "玩家: " + data.online + " / " + data.max;
                }
            }
            if (!info.isEmpty()) {
                Font infoFont = baseFont.deriveFont(Font.PLAIN, (float) INFO_FONT_SIZE);
                float infoSize = scaleFontToFit(g, infoFont, new String[] { info }, maxContentWidth);
                infoFont = baseFont.deriveFont(Font.PLAIN, infoSize);
                g.setFont(infoFont);
                g.setColor(new Color(200, 200, 200));
                drawCenteredShadowText(info, y, new Color(200, 200, 200), Color.BLACK);
            }
            y += 22;

            g.setFont(baseFont.deriveFont(Font.PLAIN, 12f));
            String timeStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            drawCenteredShadowText(timeStr, y, Color.GRAY, Color.BLACK);
            saveAndDispose(outFile);
        }

        private static String[] motdToTwoLines(String motd) {
            if (motd == null || motd.isEmpty()) return new String[] { "(无)" };
            String[] raw = motd.split("\n", 3);
            String line1 = raw[0].trim();
            String line2 = raw.length > 1 ? raw[1].trim() : "";
            if (line2.isEmpty()) return new String[] { line1 };
            return new String[] { line1, line2 };
        }

        private static List<List<TextSegment>> motdToSegmentLines(MotdResult data) {
            List<List<TextSegment>> out = new ArrayList<>();
            if (data != null && !data.motdRawLines.isEmpty()) {
                for (String raw : data.motdRawLines) {
                    List<TextSegment> segs = parseLegacyColorCodes(raw);
                    if (segs.isEmpty()) {
                        String fallback = stripFormatting(raw);
                        if (!fallback.isEmpty()) segs = List.of(new TextSegment(fallback, Color.WHITE));
                    }
                    if (!segs.isEmpty()) out.add(segs);
                }
            }
            if (out.isEmpty()) {
                String[] lines = motdToTwoLines(data != null ? data.motd : "");
                for (String line : lines) {
                    List<TextSegment> segs = new ArrayList<>();
                    segs.add(new TextSegment(line, Color.WHITE));
                    out.add(segs);
                }
            }
            return out;
        }

        private int getSegmentsWidth(Font font, List<TextSegment> segments) {
            if (segments == null || segments.isEmpty()) return 0;
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int total = 0;
            for (TextSegment seg : segments) total += fm.stringWidth(seg.text);
            return total;
        }

        private void drawCenteredSegmentsWithShadow(Font font, List<TextSegment> segments, int y) {
            if (segments == null || segments.isEmpty()) return;
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int totalW = getSegmentsWidth(font, segments);
            int x = (width - totalW) / 2;
            for (TextSegment seg : segments) {
                drawShadowText(seg.text, x, y, seg.color, Color.BLACK);
                x += fm.stringWidth(seg.text);
            }
        }

        private static float scaleFontToFitSegments(Graphics2D g, Font font, List<List<TextSegment>> segLines, int maxWidth) {
            if (segLines == null || segLines.isEmpty()) return (float) MotdImageGen.MOTD_FONT_SIZE;
            FontMetrics fm = g.getFontMetrics(font);
            int maxW = 0;
            for (List<TextSegment> segs : segLines) {
                int w = 0;
                for (TextSegment seg : segs) w += fm.stringWidth(seg.text);
                if (w > maxW) maxW = w;
            }
            if (maxW == 0 || maxW <= maxWidth) return (float) MotdImageGen.MOTD_FONT_SIZE;
            float scale = (float) MotdImageGen.MOTD_FONT_SIZE * (float) maxWidth / maxW;
            return Math.max(scale, (float) MotdImageGen.MOTD_FONT_SIZE_MIN);
        }

        private static float scaleFontToFit(Graphics2D g, Font font, String[] lines, int maxWidth) {
            if (lines == null || lines.length == 0) return (float) MotdImageGen.INFO_FONT_SIZE;
            FontMetrics fm = g.getFontMetrics(font);
            int maxW = 0;
            for (String s : lines) {
                if (s != null) {
                    int w = fm.stringWidth(s);
                    if (w > maxW) maxW = w;
                }
            }
            if (maxW == 0 || maxW <= maxWidth) return (float) MotdImageGen.INFO_FONT_SIZE;
            float scale = (float) MotdImageGen.INFO_FONT_SIZE * (float) maxWidth / maxW;
            return Math.max(scale, (float) MotdImageGen.INFO_FONT_SIZE_MIN);
        }
    }

}
