package top.yzljc.qqbot.img;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 生成并推送魔法少女の魔女审判 x Minecraft 项目进度图片到QQ群（计算天数）
 */
public class ManosabaDate {

    private static final Logger log = LoggerFactory.getLogger(ManosabaDate.class);

    static Settings settings = Config.getInstance();
    private static final long GROUP_ID = settings.getManosabaGroupId();
    private static final List<Long> admins = settings.getAdminUids();

    public static void generateDevelopDayImage() throws IOException {
        File tempDir = new File("tmp");
        if (!tempDir.exists()) tempDir.mkdirs();
        File outFile = new File(tempDir, "manoday.png");

        String imgFileName = "manosaba.png";
        File bgFile = new File(imgFileName);
        if (!bgFile.exists()) throw new IOException("未找到背景图片：" + imgFileName);

        BufferedImage img = ImageIO.read(bgFile);
        Graphics2D g = img.createGraphics();

        // 抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 项目开始日
        LocalDate start = LocalDate.of(2025, 11, 13);
        LocalDate now = LocalDate.now();
        long days = ChronoUnit.DAYS.between(start, now) + 1;
        if (days < 1) days = 1;

        String line1 = "你说的对，但是今天是";
        String line2 = "【魔法少女の魔女审判 x Minecraft】项目";
        String line3 = "开发的第 " + days + " 天";

// TODO: 此处图片生成和字体检测逻辑与HappyNewYear.java中重复，考虑抽象到工具类
        Font font;
        File fontFile = new File("MinecraftAE.ttf");
        if (fontFile.exists()) {
            try {
                font = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(Font.BOLD, 28f);
            } catch (Exception e) {
                log.warn("自定义字体加载失败，将使用默认字体：{}", e.getMessage());
                font = new Font(Font.SANS_SERIF, Font.BOLD, 28);
            }
        } else {
            log.warn("字体文件 MinecraftAE.ttf 未找到，将使用默认无衬线字体");
            font = new Font(Font.SANS_SERIF, Font.BOLD, 28);
        }
        g.setFont(font);

        Color cyan = new Color(0, 200, 255);

        // 计算居中位置与行距
        FontMetrics fm = g.getFontMetrics(font);
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        int lineHeight = fm.getHeight();
        int padding = 20;
        String[] lines = {line1, line2, line3};
        int totalHeight = lineHeight * lines.length;
        int firstY = imgH - padding - totalHeight + fm.getAscent();

        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i];
            int textWidth = fm.stringWidth(line);
            int x = (imgW - textWidth) / 2;
            int y = firstY + i * lineHeight;

            // 阴影
            g.setColor(new Color(0, 0, 0, 128));
            g.drawString(line, x + 2, y + 2);
            // 主体
            g.setColor(cyan);
            g.drawString(line, x, y);
        }

        g.dispose();
        ImageIO.write(img, "png", outFile);
    }

    public static void processManodate(JsonNode json) {
        String postType = json.path("post_type").asText("");
        if (!"message".equals(postType)) return;
        String messageType = json.path("message_type").asText("");
        if (!"group".equals(messageType)) return;
        String rawMessage = json.path("raw_message").asText("").trim().toLowerCase();
        long groupId = json.path("group_id").asLong();
        long userId = json.path("user_id").asLong();

        if (!admins.contains(userId)){
            return;
        }

        if ("/manodate".equals(rawMessage)) {

            sendAndNotifyToGroup(); // 保持原逻辑推送到固定群
            log.info("manodate 指令触发图片推送：{}", groupId);
        }
    }

    public static boolean sendAndNotifyToGroup() {
        // 使用固定的 GROUP_ID
        return sendAndNotifyToGroup(GROUP_ID);
    }

    // 重载方法，支持推送到指定群
    public static boolean sendAndNotifyToGroup(long targetGroupId) {
        File tempFile = new File("tmp", "manoday.png");
        try {
            generateDevelopDayImage();
            if (!tempFile.exists()) {
                log.warn("manoday.png 生成失败，图片不存在");
                return false;
            }

            // 图片转base64
            byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
            String base64Img = Base64.getEncoder().encodeToString(imgBytes);

            MessageSender.sendGroupMessage(targetGroupId, null, base64Img);

            return true;

        } catch (Exception ex) {
            log.error("推送图片异常：{}, ex.getMessage()");
            ex.printStackTrace();
            return false;
        } finally {
            // 自动删除
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    log.info("临时图片已自动清理：{}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    public static void startAutoDailyTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        Runnable task = () -> sendAndNotifyToGroup();

        long initialDelay = computeInitialDelayToMidnight11();
        long period = 24 * 60 * 60; // 24小时，单位秒

        scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS);
        log.info("已启动每日0:00:01自动推送任务");
    }

    private static long computeInitialDelayToMidnight11() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.toLocalDate().atStartOfDay().plusDays(0).plusSeconds(1);
        if (!now.isBefore(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).toSeconds();
    }
}
