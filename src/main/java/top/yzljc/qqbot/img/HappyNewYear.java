package top.yzljc.qqbot.img;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.messages.MessageSender;
import top.yzljc.qqbot.utils.GroupList; // 引入刚才创建的类

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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HappyNewYear {

    /**
     * 生成项目第N天图片：tmp/manoday.png
     * @throws IOException 生成异常
     */
    public static void generateDevelopDayImage() throws IOException {
        File tempDir = new File("tmp");
        if (!tempDir.exists()) tempDir.mkdirs();
        File outFile = new File(tempDir, "manoday.png");

        String imgFileName = "manosaba.png";
        File bgFile = new File(imgFileName);
        if (!bgFile.exists()) throw new IOException("未找到背景图片: " + imgFileName);

        BufferedImage img = ImageIO.read(bgFile);
        Graphics2D g = img.createGraphics();

        // 抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 项目开始日
        LocalDate targetDate = LocalDate.of(2026, 2, 17);
        LocalDate now = LocalDate.now();
        long daysUntil = ChronoUnit.DAYS.between(now, targetDate);

        if (daysUntil < 0) {
            daysUntil = 0;
        }

        String line1 = "距离 2026 年春节还有";
        String line2 = daysUntil + " 天";

        // 第一行使用较小的字体（28号）
        Font font1;
        File fontFile = new File("MinecraftAE.ttf");
        if (fontFile.exists()) {
            try {
                font1 = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(Font.BOLD, 35f);
            } catch (Exception e) {
                System.err.println("[ImgWarning] 自定义字体加载失败，将使用默认字体: " + e.getMessage());
                font1 = new Font(Font.SANS_SERIF, Font.BOLD, 35);
            }
        } else {
            System.err.println("[ImgWarning] 字体文件 MinecraftAE.ttf 未找到，将使用默认无衬线字体。");
            font1 = new Font(Font.SANS_SERIF, Font.BOLD, 35);
        }

        // 第二行（天数）使用较大的字体（48号）
        Font font2;
        if (fontFile.exists()) {
            try {
                font2 = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(Font.BOLD, 55f);
            } catch (Exception e) {
                System.err.println("[ImgWarning] 自定义字体加载失败，将使用默认字体: " + e.getMessage());
                font2 = new Font(Font.SANS_SERIF, Font.BOLD, 55);
            }
        } else {
            font2 = new Font(Font.SANS_SERIF, Font.BOLD, 55);
        }

        Color red = new Color(229, 31, 86);

        // 计算图片尺寸
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        int padding = 20;

        // 获取第一行字体指标
        g.setFont(font1);
        FontMetrics fm1 = g.getFontMetrics(font1);
        int line1Width = fm1.stringWidth(line1);
        int line1Height = fm1.getHeight();

        // 获取第二行字体指标
        g.setFont(font2);
        FontMetrics fm2 = g.getFontMetrics(font2);
        int line2Width = fm2.stringWidth(line2);
        int line2Height = fm2.getHeight();

        // 计算总高度（增加行间距）
        int totalHeight = line1Height + line2Height + 15; // 增加15像素的行间距

        // 第一行的位置（往上一些）
        int line1Y = imgH - padding - totalHeight + fm1.getAscent() + 10;
        int line1X = (imgW - line1Width) / 2;

        // 第二行的位置（与第一行保持间距）
        int line2Y = line1Y + line1Height + 15; // 距离第一行15像素
        int line2X = (imgW - line2Width) / 2;

        // 绘制第一行（小字）
        g.setFont(font1);
        // 阴影
        g.setColor(new Color(0, 0, 0, 128));
        g.drawString(line1, line1X + 2, line1Y + 2);
        // 主体
        g.setColor(red);
        g.drawString(line1, line1X, line1Y);

        // 绘制第二行（大字）
        g.setFont(font2);
        // 阴影
        g.setColor(new Color(0, 0, 0, 128));
        g.drawString(line2, line2X + 2, line2Y + 2);
        // 主体
        g.setColor(red);
        g.drawString(line2, line2X, line2Y);

        g.dispose();
        ImageIO.write(img, "png", outFile);
    }

    public static void processManodate(JsonNode json) {
        // 只监听 group 消息且内容为 manodate
        String postType = json.path("post_type").asText("");
        if (!"message".equals(postType)) return;
        String messageType = json.path("message_type").asText("");
        if (!"group".equals(messageType)) return;
        String rawMessage = json.path("raw_message").asText("").trim().toLowerCase();
        long groupId = json.path("group_id").asLong();

        if ("/happynewyear".equals(rawMessage)) {
            sendToSingleGroup(groupId);
            System.out.println("[INFO] 新年倒计时指令触发图片推送：" + groupId);
        }
    }

    public static boolean sendToAllGroups() {
        File tempFile = new File("tmp", "manoday.png");
        try {
            generateDevelopDayImage();
            if (!tempFile.exists()) {
                System.err.println("[INFO] manoday.png 生成失败，图片不存在！");
                return false;
            }

            byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
            String base64Img = Base64.getEncoder().encodeToString(imgBytes);

            Set<Long> groupIds = GroupList.fetchAllGroupIds();
            if (groupIds.isEmpty()) {
                System.out.println("[INFO] 未获取到任何群号，跳过推送。");
                return false;
            }

            System.out.println("[INFO] 开始向 " + groupIds.size() + " 个群推送图片...");
            int count = 0;
            for (Long gid : groupIds) {
                MessageSender.sendGroupMessage(gid, null, base64Img);
                count++;

                try { Thread.sleep(200); } catch (InterruptedException e) {}
            }
            System.out.println("[INFO] 推送完成，共发送给 " + count + " 个群。");

            return true;

        } catch (Exception ex) {
            System.err.println("[INFO] 群发图片异常: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        } finally {
            // 5. 任务完成后统一删除
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    System.out.println("[INFO] 临时图片已自动清理");
                }
            }
        }
    }

    public static void sendToSingleGroup(long targetGroupId) {
        File tempFile = new File("tmp", "manoday.png");
        try {
            generateDevelopDayImage();
            if (tempFile.exists()) {
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                MessageSender.sendGroupMessage(targetGroupId, null, base64Img);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    public static void startAutoDailyTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // 每日任务调用群发方法
        Runnable task = HappyNewYear::sendToAllGroups;

        long initialDelay = computeInitialDelayToMidnight11();
        long period = 24 * 60 * 60; // 24小时，单位秒

        scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS);
        System.out.println("[INFO] 已启动每日0:00:01自动推送任务");
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