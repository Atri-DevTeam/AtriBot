package top.yzljc.qqbot.feature.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.image.AbstractImage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommitDisplay extends AbstractImage {

    private static final Logger log = LoggerFactory.getLogger(CommitDisplay.class);
    private static Font CUSTOM_FONT;

    static {
        try {
            File fontFile = new File("MinecraftAE.ttf");
            if (fontFile.exists()) {
                CUSTOM_FONT = Font.createFont(Font.TRUETYPE_FONT, fontFile);
            }
        } catch (Exception e) {
            log.warn("自定义字体加载失败，将使用系统默认字体");
        }
    }

    private Font getSmartFont(int style, float size) {
        if (CUSTOM_FONT != null) {
            return CUSTOM_FONT.deriveFont(style, size);
        }
        return new Font(Font.SANS_SERIF, style, (int) size);
    }

    public String generateBase64(GithubPayload payload) {
        try {
            try {
                initFromBackground("github_background.png");
            } catch (IOException e) {
                initBlank(900, 500); // 稍微加高一点以容纳详细信息
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 23, 29), 0, height, new Color(10, 10, 10));
                g.setPaint(gp);
                g.fillRect(0, 0, width, height);
            }

            int margin = 100;
            int cardX = margin;
            int cardY = margin;
            int cardW = width - (margin * 2);
            int cardH = height - (margin * 2);

            g.setColor(new Color(22, 27, 34, 210)); // GitHub Dark Dimmed 背景色
            g.fill(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 20, 20));

            g.setStroke(new BasicStroke(1.0f));
            g.setColor(new Color(255, 255, 255, 40));
            g.draw(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 20, 20));

            int padding = 35;
            int startX = cardX + padding;
            int rightX = cardX + cardW - padding;
            int currentY = cardY + padding + 10;

            g.setFont(getSmartFont(Font.BOLD, 30));
            g.setColor(new Color(230, 237, 243));
            g.drawString(payload.repoName, startX, currentY);

            String shortHash = payload.hash.length() > 7 ? payload.hash.substring(0, 7) : payload.hash;
            String hashText = "#" + shortHash;
            g.setFont(getSmartFont(Font.PLAIN, 22));
            g.setColor(new Color(139, 148, 158)); // 灰色
            int hashW = g.getFontMetrics().stringWidth(hashText);
            g.drawString(hashText, rightX - hashW, currentY);

            currentY += 45;
            int avatarSize = 40;
            drawAvatar(payload.avatarUrl, startX, currentY - 25, avatarSize);

            g.setFont(getSmartFont(Font.BOLD, 20));
            g.setColor(new Color(201, 209, 217));
            g.drawString(payload.pusherName, startX + avatarSize + 15, currentY);


            String branchText = "变动分支 " + payload.branch;
            int nameW = g.getFontMetrics().stringWidth(payload.pusherName);
            g.setFont(getSmartFont(Font.PLAIN, 18));
            g.setColor(new Color(147, 83, 221)); // Blue
            g.drawString(branchText, startX + avatarSize + 15 + nameW + 15, currentY);

            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
            g.setFont(getSmartFont(Font.PLAIN, 18));
            g.setColor(new Color(139, 148, 158));
            int timeW = g.getFontMetrics().stringWidth(timeStr);
            g.drawString(timeStr, rightX - timeW, currentY);

            currentY += 30;
            g.setColor(new Color(48, 54, 61)); // GitHub Border Color
            g.drawLine(startX, currentY, rightX, currentY);

            currentY += 45;
            int bodyStartY = drawCommitTitle(payload.message, startX, currentY);

            drawCommitBody(payload.message, startX, bodyStartY + 35, cardH - 180);

            int bottomY = cardY + cardH - 25;
            drawStats(payload, rightX, bottomY);

            // 输出
            if (g != null) g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int drawCommitTitle(String rawMsg, int x, int y) {
        String firstLine = rawMsg.split("\n")[0];
        Pattern pattern = Pattern.compile("^(\\w+)(?:\\(([^)]+)\\))?[:：]\\s*(.+)");
        Matcher matcher = pattern.matcher(firstLine);

        int currentX = x;
        Font tagFont = getSmartFont(Font.BOLD, 16);
        Font titleFont = getSmartFont(Font.BOLD, 24);

        if (matcher.find()) {
            String type = matcher.group(1);
            String scope = matcher.group(2);
            String subject = matcher.group(3);

            // Type Tag
            int typeW = drawTag(type.toUpperCase(), currentX, y, getTypeColor(type), tagFont);
            currentX += typeW + 12;

            // Scope Tag
            if (scope != null) {
                int scopeW = drawTag(scope.toUpperCase(), currentX, y, new Color(56, 139, 253), tagFont);
                currentX += scopeW + 12;
            }

            g.setFont(titleFont);
            g.setColor(Color.WHITE);
            g.drawString(subject, currentX, y);
        } else {
            g.setFont(titleFont);
            g.setColor(Color.WHITE);
            g.drawString(firstLine, x, y);
        }
        return y;
    }

    private void drawCommitBody(String rawMsg, int x, int y, int maxHeight) {
        if (rawMsg == null || rawMsg.isEmpty()) return;

        String normalizedMsg = rawMsg.replace("\r\n", "\n");

        String[] allLines = normalizedMsg.split("\n");

        if (allLines.length < 2) {
            return;
        }

        g.setFont(getSmartFont(Font.PLAIN, 20));
        g.setColor(new Color(160, 165, 170)); // 浅灰色文字

        int lineHeight = 28;
        int currentY = y;

        for (int i = 1; i < allLines.length; i++) {
            String line = allLines[i];

            // 简单的防溢出控制
            if (currentY > maxHeight + y) {
                g.drawString("...", x, currentY);
                break;
            }
            g.drawString(line, x, currentY);
            currentY += lineHeight;
        }
    }

    private void drawStats(GithubPayload payload, int rightX, int baselineY) {

        g.setFont(getSmartFont(Font.BOLD, 22));

        String removed = "-" + payload.removedCount;
        String added = "+" + payload.addedCount;
        String files = payload.changedFilesCount + " files changed";

        // 从右向左绘制
        g.setColor(new Color(248, 81, 73)); // Red
        int remW = g.getFontMetrics().stringWidth(removed);
        g.drawString(removed, rightX - remW, baselineY);

        g.setColor(new Color(63, 185, 80)); // Green
        int addW = g.getFontMetrics().stringWidth(added);
        g.drawString(added, rightX - remW - 10 - addW, baselineY);

        // 括号分隔
        g.setColor(new Color(139, 148, 158));

        // 重新计算总宽度
        int totalW = remW + 10 + addW + 15 + g.getFontMetrics().stringWidth(files);
        int startX = rightX - totalW;

        g.setColor(new Color(139, 148, 158));
        g.drawString(files, startX, baselineY);

        int fileW = g.getFontMetrics().stringWidth(files);

        g.setColor(new Color(63, 185, 80));
        g.drawString(added, startX + fileW + 15, baselineY);

        g.setColor(new Color(248, 81, 73));
        g.drawString(removed, startX + fileW + 15 + addW + 10, baselineY);
    }

    private int drawTag(String text, int x, int y, Color bg, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 16;
        int h = 26;
        int rectY = y - 20;

        g.setColor(bg);
        g.fillRoundRect(x, rectY, w, h, 8, 8);

        g.setColor(Color.WHITE);
        // 居中绘制文字
        int textX = x + (w - fm.stringWidth(text)) / 2;
        g.drawString(text, textX, y - 1); // 微调Y
        return w;
    }

    private void drawAvatar(String url, int x, int y, int size) {
        try {
            BufferedImage img = ImageIO.read(new URL(url));
            // 开启抗锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new Ellipse2D.Float(x, y, size, size));
            g.drawImage(img, x, y, size, size, null);
            g.setClip(null);

            // 描边
            g.setColor(new Color(200, 200, 200, 100));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(x, y, size, size);
        } catch (Exception e) {
            g.setColor(new Color(40, 40, 40));
            g.fillOval(x, y, size, size);
        }
    }

    private Color getTypeColor(String type) {
        if (type == null) return Color.GRAY;
        return switch (type.toUpperCase()) {
            case "FEAT" -> new Color(35, 134, 54); // Green
            case "FIX" -> new Color(218, 54, 51); // Red
            case "TO" -> new Color(137, 87, 229); // Purple
            case "DOCS" -> new Color(31, 111, 235); // Blue
            case "STYLE" -> new Color(210, 153, 34); // Yellow
            case "REFACTOR" -> new Color(247, 129, 102); // Orange
            case "PERF" -> new Color(56, 219, 131); // Cyan
            case "TEST" -> new Color(106, 115, 125); // Gray
            case "CHORE" -> new Color(149, 157, 165); // Light Gray
            case "MERGE" -> new Color(110, 84, 148);
            default -> new Color(110, 118, 129);
        };
    }

    public static class GithubPayload {
        public String repoName = "Unknown";
        public String branch = "Main";
        public String pusherName = "Unknown";
        public String avatarUrl = "";
        public String hash = "";
        public String message = "";
        public int addedCount = 0;
        public int removedCount = 0;
        public int changedFilesCount = 0;
    }
}