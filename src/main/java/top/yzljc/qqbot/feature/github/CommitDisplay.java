package top.yzljc.qqbot.feature.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.image.AbstractImage;
import top.yzljc.qqbot.config.ConfigFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommitDisplay extends AbstractImage {

    private static final Logger log = LoggerFactory.getLogger(CommitDisplay.class);

    public String generateBase64(GithubPayload payload) {
        try {
            try {
                initFromBackground(ConfigFile.IMG_GITHUB.getFileName());
            } catch (Exception e) {
                initBlank(1200, 700);
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 23, 29), 0, height, new Color(10, 10, 10));
                g.setPaint(gp);
                g.fillRect(0, 0, width, height);
            }

            int marginX = 40;
            int cardW = width - (marginX * 2);

            int card1Y = 30;
            int card1H = 110;

            int card3H = 45;
            int card3Y = height - 30 - card3H;

            int gap = 20;
            int card2Y = card1Y + card1H + gap;
            int card2H = card3Y - gap - card2Y;

            Color cardBg = new Color(30, 35, 42, 210);
            Color cardBorder = new Color(255, 255, 255, 30);

            g.setColor(cardBg);
            g.fill(new RoundRectangle2D.Float(marginX, card1Y, cardW, card1H, 20, 20));
            g.setColor(cardBorder);
            g.draw(new RoundRectangle2D.Float(marginX, card1Y, cardW, card1H, 20, 20));
            g.setColor(cardBg);
            g.fill(new RoundRectangle2D.Float(marginX, card2Y, cardW, card2H, 20, 20));
            g.setColor(cardBorder);
            g.draw(new RoundRectangle2D.Float(marginX, card2Y, cardW, card2H, 20, 20));
            g.setColor(cardBg);
            g.fill(new RoundRectangle2D.Float(marginX, card3Y, cardW, card3H, 15, 15));
            g.setColor(cardBorder);
            g.draw(new RoundRectangle2D.Float(marginX, card3Y, cardW, card3H, 15, 15));

            int innerPaddingX = marginX + 30;
            int rightAlignX = marginX + cardW - 30;

            int avatarSize = 76;
            int avatarY = card1Y + (card1H - avatarSize) / 2;
            drawAvatar(payload.avatarUrl, innerPaddingX, avatarY, avatarSize);

            int textStartX = innerPaddingX + avatarSize + 25;
            int nameBaselineY = avatarY + 32;
            g.setFont(loadFont(Font.BOLD, 32));
            g.setColor(new Color(201, 209, 217));
            g.drawString(payload.pusherName, textStartX, nameBaselineY);

            int nameW = g.getFontMetrics().stringWidth(payload.pusherName);
            String shortRepo = payload.repoName;
            if (shortRepo != null && shortRepo.contains("/")) {
                shortRepo = shortRepo.substring(shortRepo.lastIndexOf('/') + 1);
            }
            drawTag(shortRepo, textStartX + nameW + 15, nameBaselineY - 4, new Color(56, 139, 253, 180), loadFont(Font.BOLD, 22));

            int branchBaselineY = nameBaselineY + 34;
            g.setFont(loadFont(Font.PLAIN, 26));
            g.setColor(new Color(28, 219, 243));
            g.drawString("变动分支 " + payload.branch, textStartX, branchBaselineY);

            String safeHash = payload.hash != null ? payload.hash : "";
            String shortHash = safeHash.length() > 7 ? safeHash.substring(0, 7) : safeHash;
            g.setFont(loadFont(Font.BOLD, 26));
            g.setColor(new Color(139, 148, 158));
            int hashW = g.getFontMetrics().stringWidth("#" + shortHash);
            g.drawString("#" + shortHash, rightAlignX - hashW, nameBaselineY);

            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
            g.setFont(loadFont(Font.PLAIN, 24));
            g.setColor(new Color(139, 148, 158));
            int timeW = g.getFontMetrics().stringWidth(timeStr);
            g.drawString(timeStr, rightAlignX - timeW, branchBaselineY);

            String cleanMessage = payload.message;
            if (cleanMessage != null) {
                cleanMessage = cleanMessage.replace("\r\n", "\n").replace("\\", " ").replace("\"", " ");
            }

            int titleBaselineY = card2Y + 50;
            int bodyStartY = drawCommitTitle(cleanMessage, innerPaddingX, titleBaselineY);
            int statsBaselineY = card2Y + card2H - 25;
            int maxBodyY = statsBaselineY - 35;

            drawCommitBody(cleanMessage, innerPaddingX, bodyStartY + 50, maxBodyY);

            drawStats(payload, rightAlignX, statsBaselineY);

            String copyRightText = "Copyrights © 2026 YZ_Ljc_. All Rights Reserved.";
            g.setFont(loadFont(Font.BOLD, 20));
            g.setColor(new Color(200, 205, 210));
            FontMetrics copyFm = g.getFontMetrics();
            int copyW = copyFm.stringWidth(copyRightText);
            int copyX = marginX + (cardW - copyW) / 2;
            int copyY = card3Y + (card3H / 2) + (copyFm.getAscent() / 2) - 3;
            g.drawString(copyRightText, copyX, copyY);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());

        } catch (Exception e) {
            log.error("生成 Github Commit 图片失败", e);
            return null;
        } finally {
            if (g != null) {
                g.dispose();
                g = null;
            }
            if (image != null) {
                image.flush();
                image = null;
            }
        }
    }

    private int drawCommitTitle(String rawMsg, int x, int y) {
        if (rawMsg == null || rawMsg.isEmpty()) return y;

        String firstLine = rawMsg.split("\n")[0];
        Pattern pattern = Pattern.compile("^(\\w+)(?:\\(([^)]+)\\))?[:：]\\s*(.+)");
        Matcher matcher = pattern.matcher(firstLine);

        int currentX = x;
        // 因为父类 AbstractImage 已经全局缓存了字体，这里直接调用 loadFont(..) 非常快，不会有性能问题
        Font tagFont = loadFont(Font.BOLD, 22);
        Font titleFont = loadFont(Font.BOLD, 30);

        if (matcher.find()) {
            String type = matcher.group(1);
            String scope = matcher.group(2);
            String subject = matcher.group(3);

            int typeW = drawTag(type.toUpperCase(), currentX, y, getTypeColor(type), tagFont);
            currentX += typeW + 15;

            if (scope != null) {
                int scopeW = drawTag(scope.toUpperCase(), currentX, y, new Color(56, 139, 253), tagFont);
                currentX += scopeW + 15;
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

    private void drawCommitBody(String cleanMsg, int x, int y, int maxY) {
        if (cleanMsg == null || cleanMsg.isEmpty()) return;

        String[] allLines = cleanMsg.split("\n");
        if (allLines.length < 2) return;

        g.setFont(loadFont(Font.PLAIN, 26));
        g.setColor(new Color(160, 165, 170));

        int lineHeight = 38;
        int currentY = y;

        for (int i = 1; i < allLines.length; i++) {
            String line = allLines[i];

            if (currentY + lineHeight > maxY && i < allLines.length - 1) {
                g.drawString(line + " ...", x, currentY);
                break;
            } else if (currentY > maxY) {
                break;
            }

            g.drawString(line, x, currentY);
            currentY += lineHeight;
        }
    }

    private void drawStats(GithubPayload payload, int rightX, int baselineY) {
        g.setFont(loadFont(Font.BOLD, 24));
        FontMetrics fm = g.getFontMetrics();

        String removed = "-" + payload.removedCount;
        String added = "+" + payload.addedCount;
        String files = payload.changedFilesCount + " files changed";

        int remW = fm.stringWidth(removed);
        int addW = fm.stringWidth(added);
        int fileW = fm.stringWidth(files);

        int currentX = rightX - remW;
        g.setColor(new Color(248, 81, 73));
        g.drawString(removed, currentX, baselineY);

        currentX -= (addW + 10);
        g.setColor(new Color(63, 185, 80));
        g.drawString(added, currentX, baselineY);

        currentX -= (fileW + 15);
        g.setColor(new Color(139, 148, 158));
        g.drawString(files, currentX, baselineY);
    }

    private int drawTag(String text, int x, int y, Color bg, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 20;
        int h = fm.getHeight() + 8;
        int rectY = y - fm.getAscent() - 4;

        g.setColor(bg);
        g.fillRoundRect(x, rectY, w, h, 10, 10);

        g.setColor(Color.WHITE);
        int textX = x + (w - fm.stringWidth(text)) / 2;
        g.drawString(text, textX, y);
        return w;
    }

    private void drawAvatar(String url, int x, int y, int size) {
        BufferedImage img = null;
        try {
            img = ImageIO.read(new URI(url).toURL());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setClip(new Ellipse2D.Float(x, y, size, size));
            g.drawImage(img, x, y, size, size, null);
            g.setClip(null);

            g.setColor(new Color(200, 200, 200, 100));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(x, y, size, size);
        } catch (Exception e) {
            g.setClip(null);
            g.setColor(new Color(40, 40, 40));
            g.fillOval(x, y, size, size);
        } finally {
            if (img != null) {
                img.flush();
            }
        }
    }

    private Color getTypeColor(String type) {
        if (type == null) return Color.GRAY;
        return switch (type.toUpperCase()) {
            case "FEAT" -> new Color(35, 134, 54);
            case "FIX" -> new Color(218, 54, 51);
            case "TO" -> new Color(137, 87, 229);
            case "DOCS" -> new Color(31, 111, 235);
            case "STYLE" -> new Color(210, 153, 34);
            case "REFACTOR" -> new Color(247, 129, 102);
            case "PERF" -> new Color(56, 219, 131);
            case "TEST" -> new Color(106, 115, 125);
            case "CHORE" -> new Color(149, 157, 165);
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