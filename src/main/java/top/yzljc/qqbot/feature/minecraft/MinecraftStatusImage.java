package top.yzljc.qqbot.feature.minecraft;

import top.yzljc.qqbot.botkits.image.AbstractImage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecraftStatusImage {

    private static final Logger log = LoggerFactory.getLogger(MinecraftStatusImage.class);

    // Minecraft 颜色代码映射
    private static final Map<Character, Color> MC_COLORS = new HashMap<>();
    static {
        MC_COLORS.put('0', new Color(0, 0, 0));       // Black
        MC_COLORS.put('1', new Color(0, 0, 170));     // Dark Blue
        MC_COLORS.put('2', new Color(0, 170, 0));     // Dark Green
        MC_COLORS.put('3', new Color(0, 170, 170));   // Dark Aqua
        MC_COLORS.put('4', new Color(170, 0, 0));     // Dark Red
        MC_COLORS.put('5', new Color(170, 0, 170));   // Dark Purple
        MC_COLORS.put('6', new Color(255, 170, 0));   // Gold
        MC_COLORS.put('7', new Color(170, 170, 170)); // Gray
        MC_COLORS.put('8', new Color(85, 85, 85));    // Dark Gray
        MC_COLORS.put('9', new Color(85, 85, 255));   // Blue
        MC_COLORS.put('a', new Color(85, 255, 85));   // Green
        MC_COLORS.put('b', new Color(85, 255, 255));  // Aqua
        MC_COLORS.put('c', new Color(255, 85, 85));   // Red
        MC_COLORS.put('d', new Color(255, 85, 255));  // Light Purple
        MC_COLORS.put('e', new Color(255, 255, 85));  // Yellow
        MC_COLORS.put('f', new Color(255, 255, 255)); // White
    }

    public static void generateStatusImage(String serverName, String ipPort, String state, String outputPath) throws Exception {
        new Generator().generate(serverName, ipPort, state, new File(outputPath));
    }

    private static class Generator extends AbstractImage {

        public void generate(String serverName, String ipPort, String state, File outFile) throws Exception {

            initFromBackground("manoyinxi.png");

            Font baseFont = loadFont(Font.PLAIN, 1f);
            Font titleFont = baseFont.deriveFont(Font.BOLD, 30f);
            Font infoFont = baseFont.deriveFont(Font.PLAIN, 22f);
            Font stateFont = baseFont.deriveFont(Font.BOLD, 24f);

            g.setFont(titleFont);
            drawShadowText("Minecraft服务器状态", 20, 50, Color.WHITE, Color.BLACK);

            g.setFont(infoFont);
            drawShadowText("名称: " + serverName, 25, 90, Color.WHITE, Color.BLACK);
            drawShadowText("地址: " + ipPort, 25, 130, Color.WHITE, Color.BLACK);

            Color stateColor = state.equals("在线") ? new Color(80, 200, 80) : new Color(200, 80, 80);
            g.setFont(stateFont);
            drawShadowText("状态: " + state, 25, 170, stateColor, Color.BLACK);

            try {
                ServerStatusInfo statusInfo = fetchServerStatus(ipPort);
                if (statusInfo != null) {
                    drawBottomRightMotdAndIcon(statusInfo, baseFont);
                    if (statusInfo.icon != null) log.info("服务器 icon 成功添加到图片右下角");
                }
            } catch (Exception e) {
                log.error("icon/motd 获取或绘制时异常：", e);
            }

            saveAndDispose(outFile);
        }

        private void drawBottomRightMotdAndIcon(ServerStatusInfo info, Font baseFont) {
            BufferedImage icon = info.icon;
            List<List<TextSegment>> motdLines = info.motdStructure;

            int iconSize = 70;
            int fontSizeMain = 18;
            int fontSizeSub = 16;
            int fontSizeInfo = 14;
            int margin = 10;
            int midPad = 12;
            int lineSpacing = 4;

            Font mainFont = baseFont.deriveFont(Font.BOLD, (float)fontSizeMain);
            Font subFont = baseFont.deriveFont(Font.PLAIN, (float)fontSizeSub);
            Font infoFont = baseFont.deriveFont(Font.PLAIN, (float)fontSizeInfo);

            List<TextSegment> line1Segs = (motdLines != null && !motdLines.isEmpty()) ? motdLines.get(0) : new ArrayList<>();
            List<TextSegment> line2Segs = (motdLines != null && motdLines.size() > 1) ? motdLines.get(1) : new ArrayList<>();

            String infoStr = "";
            if (info.version != null && !info.version.isEmpty()) infoStr += "Ver: " + info.version;
            if (info.maxPlayers > 0) {
                if (!infoStr.isEmpty()) infoStr += " | ";
                infoStr += "Online: " + info.onlinePlayers + "/" + info.maxPlayers;
            }

            int width1 = getSegmentsWidth(mainFont, line1Segs);
            int width2 = getSegmentsWidth(subFont, line2Segs);
            g.setFont(infoFont);
            int widthInfo = g.getFontMetrics().stringWidth(infoStr);

            int maxTextW = Math.max(widthInfo, Math.max(width1, width2));
            int contentW = (icon != null ? (iconSize + midPad) : 0) + maxTextW;
            int boxW = contentW + margin * 2;

            int textBlockH = 0;
            if (!line1Segs.isEmpty()) textBlockH += fontSizeMain + lineSpacing;
            if (!line2Segs.isEmpty()) textBlockH += fontSizeSub + lineSpacing;
            if (!infoStr.isEmpty()) textBlockH += fontSizeInfo + lineSpacing;
            if (textBlockH > 0) textBlockH -= lineSpacing;

            int boxH = Math.max(iconSize, textBlockH) + margin * 2;
            int startX = width - boxW - margin - 5;
            int startY = height - boxH - margin - 5;

            // 绘制背景
            g.setColor(new Color(30, 30, 30, 180));
            g.fillRoundRect(startX, startY, boxW, boxH, 14, 14);

            int textStartX = startX + margin;
            if (icon != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int iconY = startY + (boxH - iconSize) / 2;
                g.drawImage(icon, startX + margin, iconY, iconSize, iconSize, null);
                textStartX += iconSize + midPad;
            }

            int currentY = startY + (boxH - textBlockH) / 2;

            if (!line1Segs.isEmpty()) {
                drawSegments(mainFont, line1Segs, textStartX, currentY + fontSizeMain);
                currentY += fontSizeMain + lineSpacing;
            }
            if (!line2Segs.isEmpty()) {
                drawSegments(subFont, line2Segs, textStartX, currentY + fontSizeSub);
                currentY += fontSizeSub + lineSpacing;
            }
            if (!infoStr.isEmpty()) {
                g.setFont(infoFont);
                g.setColor(Color.LIGHT_GRAY);
                g.drawString(infoStr, textStartX, currentY + fontSizeInfo);
            }
        }

        private int getSegmentsWidth(Font font, List<TextSegment> segments) {
            if (segments == null || segments.isEmpty()) return 0;
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int totalWidth = 0;
            for (TextSegment seg : segments) {
                totalWidth += fm.stringWidth(seg.text);
            }
            return totalWidth;
        }

        private void drawSegments(Font font, List<TextSegment> segments, int x, int y) {
            if (segments == null || segments.isEmpty()) return;
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int currentX = x;
            for (TextSegment seg : segments) {
                g.setColor(seg.color);
                g.drawString(seg.text, currentX, y);
                currentX += fm.stringWidth(seg.text);
            }
        }
    }

    private static class TextSegment {
        String text; Color color;
        TextSegment(String t, Color c) { text = t; color = c; }
    }

    private static ServerStatusInfo fetchServerStatus(String ipPort) {
        String api = "https://api.mcstatus.io/v2/status/java/" + ipPort;
        try {
            log.debug("请求服务器API：{}", api);
            HttpURLConnection conn = (HttpURLConnection) new URI(api).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if(conn.getResponseCode() != 200) return null;

            StringBuilder jsonBuilder = new StringBuilder();
            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) jsonBuilder.append(line);
            }
            String jsonString = jsonBuilder.toString();
            JsonParser parser = new JsonParser(jsonString);

            String iconBase64Full = parser.extractString("icon");
            BufferedImage icon = null;
            if (iconBase64Full != null && iconBase64Full.contains("base64,")) {
                try {
                    String base64Data = iconBase64Full.split("base64,")[1];
                    byte[] imgBytes = Base64.getDecoder().decode(base64Data);
                    icon = ImageIO.read(new ByteArrayInputStream(imgBytes));
                } catch (Exception ex) {
                    log.warn("icon 解码错误：{}", ex.getMessage());
                }
            }

            List<List<TextSegment>> motdStructure = new ArrayList<>();
            String motdObj = parser.extractObject("motd");
            if (motdObj != null) {
                JsonParser motdParser = new JsonParser(motdObj);

                String rawMotd = motdParser.extractString("raw");
                if (rawMotd != null && !rawMotd.isEmpty()) {
                    String normalizedMotd = rawMotd.replace("\\n", "\n");

                    String[] lines = normalizedMotd.split("\n");
                    for (String line : lines) {
                        motdStructure.add(parseLegacyColorCodes(line));
                    }
                } else {
                    List<String> rawList = motdParser.extractStringArray("raw");
                    if (rawList != null) {
                        for (String line : rawList) {
                            motdStructure.add(parseLegacyColorCodes(line));
                        }
                    }
                }
            }

            String version = "";
            String versionObj = parser.extractObject("version");
            if (versionObj != null) {
                JsonParser verParser = new JsonParser(versionObj);
                version = verParser.extractString("name_clean");
                if (version == null) version = verParser.extractString("name_raw");
            } else {
                version = parser.extractString("version");
            }

            int online = 0;
            int max = 0;
            String playersObj = parser.extractObject("players");
            if (playersObj != null) {
                JsonParser plParser = new JsonParser(playersObj);
                online = plParser.extractInt("online");
                max = plParser.extractInt("max");
            }

            return new ServerStatusInfo(icon, motdStructure, online, max, version);

        } catch (Exception e) {
            log.warn("API请求或解析异常：{}", e.getMessage());
        }
        return null;
    }

    private static List<TextSegment> parseLegacyColorCodes(String text) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null) return segments;
        Color currentColor = Color.WHITE;
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                if (buffer.length() > 0) {
                    segments.add(new TextSegment(buffer.toString(), currentColor));
                    buffer.setLength(0);
                }
                char code = text.charAt(i + 1);
                Color newColor = MC_COLORS.get(Character.toLowerCase(code));

                if (newColor != null) {
                    currentColor = newColor;
                } else if (code == 'r') {
                    currentColor = Color.WHITE;
                }
                i++;
            } else {
                buffer.append(c);
            }
        }
        if (buffer.length() > 0) {
            segments.add(new TextSegment(buffer.toString(), currentColor));
        }
        return segments;
    }

    private static class JsonParser {
        private final String json;
        public JsonParser(String json) { this.json = json; }
        public String extractString(String key) {
            int keyIdx = findKeyIndex(key);
            if (keyIdx == -1) return null;
            int valStart = json.indexOf("\"", keyIdx + key.length() + 2);
            if (valStart == -1) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = valStart + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\') {
                    if (i + 1 < json.length()) { sb.append(json.charAt(i)); sb.append(json.charAt(i+1)); i++; }
                } else if (c == '"') return decodeAndClean(sb.toString());
                else sb.append(c);
            }
            return null;
        }
        public int extractInt(String key) {
            int keyIdx = findKeyIndex(key);
            if (keyIdx == -1) return 0;
            int colonIdx = json.indexOf(":", keyIdx);
            if (colonIdx == -1) return 0;
            int numStart = -1;
            for (int i = colonIdx + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (Character.isDigit(c) || c == '-') { numStart = i; break; }
            }
            if (numStart == -1) return 0;
            StringBuilder sb = new StringBuilder();
            for (int i = numStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (Character.isDigit(c) || c == '-') sb.append(c);
                else break;
            }
            try { return Integer.parseInt(sb.toString()); } catch (NumberFormatException e) { return 0; }
        }
        public String extractObject(String key) {
            int keyIdx = findKeyIndex(key);
            if (keyIdx == -1) return null;
            int braceStart = json.indexOf("{", keyIdx);
            if (braceStart == -1) return null;
            int braceCount = 1;
            for (int i = braceStart + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                if (braceCount == 0) return json.substring(braceStart + 1, i);
            }
            return null;
        }
        public List<String> extractStringArray(String key) {
            int keyIdx = findKeyIndex(key);
            if (keyIdx == -1) return null;
            int bracketStart = json.indexOf("[", keyIdx);
            if (bracketStart == -1) return null;
            int bracketCount = 1;
            int bracketEnd = -1;
            boolean inQuote = false;
            for (int i = bracketStart + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\') { i++; continue; }
                if (c == '"') inQuote = !inQuote;
                if (!inQuote) { if (c == '[') bracketCount++; else if (c == ']') bracketCount--; }
                if (bracketCount == 0) { bracketEnd = i; break; }
            }
            if (bracketEnd == -1) return null;
            String inner = json.substring(bracketStart + 1, bracketEnd);
            return parseJsonArrayInner(inner);
        }
        private List<String> parseJsonArrayInner(String inner) {
            List<String> list = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            boolean inQuote = false;
            boolean escape = false;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (escape) { sb.append('\\'); sb.append(c); escape = false; }
                else {
                    if (c == '\\') escape = true;
                    else if (c == '"') {
                        if (inQuote) { list.add(decodeAndClean(sb.toString())); sb.setLength(0); inQuote = false; }
                        else inQuote = true;
                    } else if (inQuote) sb.append(c);
                }
            }
            return list;
        }
        private int findKeyIndex(String key) { return json.indexOf("\"" + key + "\""); }
    }

    private static String decodeAndClean(String raw) {
        String s1 = raw.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/");
        return decodeUnicode(s1);
    }

    private static String decodeUnicode(String in) {
        StringBuilder out = new StringBuilder();
        int len = in.length();
        for (int i = 0; i < len; i++) {
            char ch = in.charAt(i);
            if (ch == '\\' && i + 1 < len && in.charAt(i + 1) == 'u') {
                if (i + 5 < len) {
                    try {
                        String hex = in.substring(i + 2, i + 6);
                        out.append((char) Integer.parseInt(hex, 16));
                        i += 5;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static class ServerStatusInfo {
        public BufferedImage icon;
        public List<List<TextSegment>> motdStructure;
        public int onlinePlayers;
        public int maxPlayers;
        public String version;
        public ServerStatusInfo(BufferedImage icon, List<List<TextSegment>> motdStructure, int online, int max, String ver) {
            this.icon = icon;
            this.motdStructure = motdStructure;
            this.onlinePlayers = online;
            this.maxPlayers = max;
            this.version = ver;
        }
    }
}