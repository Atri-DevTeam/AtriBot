package top.yzljc.qqbot.img;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MinecraftStatusImage {

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

    /**
     * 生成 Minecraft 服务器状态图片
     */
    public static void generateStatusImage(String serverName, String ipPort, String state, String outputPath) throws Exception {
        // 1. 检查背景图
        String backgroundImgPath = new File("manoyinxi.png").getAbsolutePath();
        File bgFile = new File(backgroundImgPath);

        if (!bgFile.exists()) {
            throw new Exception("背景图片未找到！请确保 manoyinxi.png 在运行目录下。");
        }

        // 2. 加载自定义字体 (MinecraftAE.ttf)
        Font baseFont;
        File fontFile = new File("MinecraftAE.ttf");
        if (fontFile.exists()) {
            try {
                baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
            } catch (Exception e) {
                System.err.println("[ImgWarning] 自定义字体加载失败，将使用默认字体: " + e.getMessage());
                baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 1);
            }
        } else {
            System.err.println("[ImgWarning] 字体文件 MinecraftAE.ttf 未找到，将使用默认无衬线字体。");
            baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 1);
        }

        BufferedImage bg = ImageIO.read(bgFile);
        Graphics2D g = bg.createGraphics();

        // 3. 开启抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 4. 派生不同大小的字体
        Font titleFont = baseFont.deriveFont(Font.BOLD, 30f);
        Font infoFont = baseFont.deriveFont(Font.PLAIN, 22f);
        Font stateFont = baseFont.deriveFont(Font.BOLD, 24f);

        // 绘制标题
        g.setFont(titleFont);
        drawShadowText(g, "Minecraft服务器状态", 20, 50, Color.WHITE, Color.BLACK);

        // 绘制信息
        g.setFont(infoFont);
        drawShadowText(g, "名称: " + serverName, 25, 90, Color.WHITE, Color.BLACK);
        drawShadowText(g, "地址: " + ipPort, 25, 130, Color.WHITE, Color.BLACK);

        // 绘制状态
        Color stateColor = state.equals("在线") ? new Color(80, 200, 80) : new Color(200, 80, 80);
        g.setFont(stateFont);
        drawShadowText(g, "状态: " + state, 25, 170, stateColor, Color.BLACK);

        // 新增：获取服务器icon和motd
        ServerStatusInfo statusInfo = null;
        try {
            statusInfo = fetchServerStatus(ipPort);

            // -------- 绘制右下角icon+motd风格块 --------
            drawBottomRightMotdAndIcon(bg, statusInfo, baseFont);

            if (statusInfo != null && statusInfo.icon != null) {
                System.out.println("[DEBUG] 服务器icon成功添加到图片右下角。");
            } else {
                System.out.println("[DEBUG] 服务器icon为null，未添加。");
            }
        } catch (Exception e) {
            System.err.println("[DEBUG] icon/motd获取或绘制时异常: " + e.getMessage());
            e.printStackTrace();
        }

        g.dispose();

        // 5. 保存图片
        File outFile = new File(outputPath);
        ImageIO.write(bg, "png", outFile);

        if(!outFile.exists() || outFile.length() == 0){
            throw new Exception("图片写入失败，文件不存在或大小为0");
        }
    }

    private static void drawShadowText(Graphics2D g, String text, int x, int y, Color mainColor, Color shadowColor) {
        g.setColor(shadowColor);
        g.drawString(text, x + 2, y + 2);
        g.setColor(mainColor);
        g.drawString(text, x, y);
    }

    /**
     * 在图片右下角绘制与原版服务器列表类似的icon+motd块
     */
    private static void drawBottomRightMotdAndIcon(BufferedImage bg, ServerStatusInfo info, Font baseFont) {
        if (info == null) return;

        BufferedImage icon = info.icon;
        List<String> motdLines = info.motdLines;

        // 配置参数
        int iconSize = 70;
        int fontSizeMain = 18;
        int fontSizeSub = 16;
        int fontSizeInfo = 14;
        int margin = 10;
        int midPad = 12;
        int lineSpacing = 4;

        // 字体
        Font mainFont = baseFont.deriveFont(Font.BOLD, (float)fontSizeMain);
        Font subFont = baseFont.deriveFont(Font.PLAIN, (float)fontSizeSub);
        Font infoFont = baseFont.deriveFont(Font.PLAIN, (float)fontSizeInfo);

        // 准备文本内容
        String line1 = (motdLines != null && !motdLines.isEmpty()) ? motdLines.get(0) : "";
        String line2 = (motdLines != null && motdLines.size() > 1) ? motdLines.get(1) : "";

        // 构造附加信息字符串
        String infoStr = "";
        if (info.version != null && !info.version.isEmpty()) {
            infoStr += "Ver: " + info.version;
        }
        if (info.maxPlayers > 0) {
            if (!infoStr.isEmpty()) infoStr += " | ";
            infoStr += "Online: " + info.onlinePlayers + "/" + info.maxPlayers;
        }

        Graphics2D g = bg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. 计算文本宽度
        int width1 = getColoredStringWidth(g, mainFont, line1);
        int width2 = getColoredStringWidth(g, subFont, line2);

        g.setFont(infoFont);
        int widthInfo = g.getFontMetrics().stringWidth(infoStr);

        int maxTextW = Math.max(widthInfo, Math.max(width1, width2));

        // 2. 计算盒子总尺寸
        int contentW = (icon != null ? (iconSize + midPad) : 0) + maxTextW;
        int boxW = contentW + margin * 2;

        // 计算文本区域总高度
        int textBlockH = 0;
        if (!line1.isEmpty()) textBlockH += fontSizeMain + lineSpacing;
        if (!line2.isEmpty()) textBlockH += fontSizeSub + lineSpacing;
        if (!infoStr.isEmpty()) textBlockH += fontSizeInfo + lineSpacing;
        if (textBlockH > 0) textBlockH -= lineSpacing;

        int boxH = Math.max(iconSize, textBlockH) + margin * 2;

        // 3. 确定绘制起始位置
        int startX = bg.getWidth() - boxW - margin - 5;
        int startY = bg.getHeight() - boxH - margin - 5;

        // 4. 绘制半透明背景与圆角
        g.setColor(new Color(30, 30, 30, 180));
        g.fillRoundRect(startX, startY, boxW, boxH, 14, 14);

        // 5. 绘制Icon
        int textStartX = startX + margin;
        if (icon != null) {
            BufferedImage scaledIcon = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaledIcon.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(icon, 0, 0, iconSize, iconSize, null);
            g2.dispose();

            int iconY = startY + (boxH - iconSize) / 2;
            g.drawImage(scaledIcon, startX + margin, iconY, null);

            textStartX += iconSize + midPad;
        }

        // 6. 绘制文本
        int currentY = startY + (boxH - textBlockH) / 2; // 起始Y (Box内部相对顶部偏移)

        // 修正基线计算：Graphics.drawString 的 y 是基线
        // Line 1
        if (!line1.isEmpty()) {
            drawColoredString(g, mainFont, line1, textStartX, currentY + fontSizeMain);
            currentY += fontSizeMain + lineSpacing;
        }

        // Line 2
        if (!line2.isEmpty()) {
            // 如果第一行没有内容，currentY 保持原位，直接画第二行
            // 如果有内容，currentY 已经往下移了
            drawColoredString(g, subFont, line2, textStartX, currentY + fontSizeSub);
            currentY += fontSizeSub + lineSpacing;
        }

        // Info Line
        if (!infoStr.isEmpty()) {
            g.setFont(infoFont);
            g.setColor(Color.LIGHT_GRAY);
            g.drawString(infoStr, textStartX, currentY + fontSizeInfo);
        }

        g.dispose();
    }

    /**
     * 计算带颜色代码的字符串的绘制宽度
     */
    private static int getColoredStringWidth(Graphics2D g, Font font, String text) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        if (text == null || text.isEmpty()) return 0;

        int totalWidth = 0;
        List<TextSegment> segments = parseColorCodes(text);
        for (TextSegment seg : segments) {
            totalWidth += fm.stringWidth(seg.text);
        }
        return totalWidth;
    }

    /**
     * 绘制带颜色代码的字符串
     */
    private static void drawColoredString(Graphics2D g, Font font, String text, int x, int y) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        if (text == null || text.isEmpty()) return;

        int currentX = x;
        List<TextSegment> segments = parseColorCodes(text);

        for (TextSegment seg : segments) {
            g.setColor(seg.color);
            g.drawString(seg.text, currentX, y);
            currentX += fm.stringWidth(seg.text);
        }
    }

    /**
     * 解析形如 "§aHello §cWorld" 的字符串为颜色段
     */
    private static List<TextSegment> parseColorCodes(String text) {
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

    private static class TextSegment {
        String text;
        Color color;
        TextSegment(String t, Color c) { text = t; color = c; }
    }

    /**
     * 获取服务器icon与motd (重写版：无依赖的健壮解析)
     */
    private static ServerStatusInfo fetchServerStatus(String ipPort) {
        String api = "https://api.mcsrvstat.us/3/" + ipPort;
        try {
            System.out.println("[DEBUG] 请求服务器icon/motd的API: " + api);
            HttpURLConnection conn = (HttpURLConnection) new URL(api).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if(conn.getResponseCode() != 200){
                System.out.println("[DEBUG] icon/motd请求非200，响应: " + conn.getResponseCode());
                return null;
            }

            StringBuilder jsonBuilder = new StringBuilder();
            try (InputStream in = conn.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBuilder.append(line);
                }
            }
            String jsonString = jsonBuilder.toString();

            // ================== 解析逻辑 ==================
            JsonParser parser = new JsonParser(jsonString);

            // 1. 提取 Icon
            String iconBase64Full = parser.extractString("icon");
            BufferedImage icon = null;
            if (iconBase64Full != null && iconBase64Full.contains("base64,")) {
                try {
                    String base64Data = iconBase64Full.split("base64,")[1];
                    byte[] imgBytes = Base64.getDecoder().decode(base64Data);
                    icon = ImageIO.read(new ByteArrayInputStream(imgBytes));
                } catch (Exception ex) {
                    System.out.println("[DEBUG] Icon解码错误: " + ex.getMessage());
                }
            }

            // 2. 提取 MOTD
            // 先提取 "motd" 对象块，再在里面找 "raw"
            List<String> motdLines = new ArrayList<>();
            String motdObj = parser.extractObject("motd");
            if (motdObj != null) {
                // 在 motd 对象字符串中解析
                JsonParser motdParser = new JsonParser(motdObj);
                List<String> rawList = motdParser.extractStringArray("raw");
                if (rawList != null) {
                    motdLines = rawList;
                }
            }

            // 3. 提取 Version
            String version = parser.extractString("version");

            // 4. 提取 Players
            int online = 0;
            int max = 0;
            String playersObj = parser.extractObject("players");
            if (playersObj != null) {
                JsonParser plParser = new JsonParser(playersObj);
                online = plParser.extractInt("online");
                max = plParser.extractInt("max");
            }

            // 打印调试
            if (!motdLines.isEmpty()) {
                System.out.println("[DEBUG] 提取到 " + motdLines.size() + " 行MOTD:");
                for (String l : motdLines) System.out.println("  - " + l);
            } else {
                System.out.println("[DEBUG] 未提取到MOTD (raw字段为空或解析失败)");
            }
            System.out.println("[DEBUG] Info -> Ver:" + version + ", Players:" + online + "/" + max);

            return new ServerStatusInfo(icon, motdLines, online, max, version);

        } catch (Exception e) {
            System.out.println("[DEBUG] icon/motd接口请求或解析异常: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 一个极其简易的 JSON 解析辅助类 (只读，非递归解析整个树，按需提取)
     */
    private static class JsonParser {
        private final String json;

        public JsonParser(String json) {
            this.json = json;
        }

        // 提取 key 对应的字符串值 (处理转义)
        public String extractString(String key) {
            int keyIdx = findKeyIndex(key);
            if (keyIdx == -1) return null;

            int valStart = json.indexOf("\"", keyIdx + key.length() + 2); // 粗略跳过":"
            if (valStart == -1) return null; // 没找到值开始

            // 向后找 "，注意 \" 转义
            StringBuilder sb = new StringBuilder();
            for (int i = valStart + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\') {
                    if (i + 1 < json.length()) {
                        sb.append(json.charAt(i)); // 先把 \ 存进去，后面统一 decode
                        sb.append(json.charAt(i+1));
                        i++;
                    }
                } else if (c == '"') {
                    // 结束
                    return decodeAndClean(sb.toString());
                } else {
                    sb.append(c);
                }
            }
            return null;
        }

        // 提取 key 对应的整数值
        public int extractInt(String key) {
            int keyIdx = findKeyIndex(key);
            if (keyIdx == -1) return 0;

            // 找到冒号
            int colonIdx = json.indexOf(":", keyIdx);
            if (colonIdx == -1) return 0;

            // 找数字开始 (跳过空白)
            int numStart = -1;
            for (int i = colonIdx + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (Character.isDigit(c) || c == '-') {
                    numStart = i;
                    break;
                }
            }
            if (numStart == -1) return 0;

            // 找数字结束
            StringBuilder sb = new StringBuilder();
            for (int i = numStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (Character.isDigit(c) || c == '-') {
                    sb.append(c);
                } else {
                    break;
                }
            }
            try {
                return Integer.parseInt(sb.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // 提取 key 对应的对象块 {...} 原始字符串
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

                if (braceCount == 0) {
                    return json.substring(braceStart + 1, i); // 返回不含首尾括号的内容
                }
            }
            return null;
        }

        // 提取 key 对应的字符串数组 ["a", "b"]
        public List<String> extractStringArray(String key) {
            int keyIdx = findKeyIndex(key);
            if (keyIdx == -1) return null;

            int bracketStart = json.indexOf("[", keyIdx);
            if (bracketStart == -1) return null;

            // 找到对应的结束 ]
            int bracketCount = 1;
            int bracketEnd = -1;
            boolean inQuote = false;

            for (int i = bracketStart + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\') { i++; continue; } // 跳过转义
                if (c == '"') inQuote = !inQuote;

                if (!inQuote) {
                    if (c == '[') bracketCount++;
                    else if (c == ']') bracketCount--;
                }

                if (bracketCount == 0) {
                    bracketEnd = i;
                    break;
                }
            }

            if (bracketEnd == -1) return null;

            String inner = json.substring(bracketStart + 1, bracketEnd);
            return parseJsonArrayInner(inner);
        }

        // 解析数组内部: "str1", "str2"
        private List<String> parseJsonArrayInner(String inner) {
            List<String> list = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            boolean inQuote = false;
            boolean escape = false;

            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (escape) {
                    sb.append('\\'); // 保留转义符给 decodeAndClean 处理
                    sb.append(c);
                    escape = false;
                } else {
                    if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        if (inQuote) {
                            // 结束一个字符串
                            list.add(decodeAndClean(sb.toString()));
                            sb.setLength(0);
                            inQuote = false;
                        } else {
                            inQuote = true;
                        }
                    } else {
                        if (inQuote) sb.append(c);
                    }
                }
            }
            return list;
        }

        // 简单的查找 "key":
        private int findKeyIndex(String key) {
            // 简单实现，未处理 key 在字符串值内部的情况 (假设 key 不会出现在 values 里)
            // 严谨点应该是 "\"key\"" 且不在其他字符串内，但这里针对已知结构简化
            return json.indexOf("\"" + key + "\"");
        }
    }

    private static String decodeAndClean(String raw) {
        // 先还原被转义的 \ (如 \\u -> u, \\" -> \")
        // 但注意 Java 字符串里 \\ 实际上是单斜杠
        // raw 是从 JSON 截取的，包含 \\u00a7

        // 1. 处理 JSON 常见转义
        String s1 = raw.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/");
        // 2. 处理 Unicode (uXXXX)
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

    /** 小容器对象 */
    private static class ServerStatusInfo {
        public BufferedImage icon;
        public List<String> motdLines;
        public int onlinePlayers;
        public int maxPlayers;
        public String version;

        public ServerStatusInfo(BufferedImage icon, List<String> motdLines, int online, int max, String ver) {
            this.icon = icon;
            this.motdLines = motdLines;
            this.onlinePlayers = online;
            this.maxPlayers = max;
            this.version = ver;
        }
    }
}