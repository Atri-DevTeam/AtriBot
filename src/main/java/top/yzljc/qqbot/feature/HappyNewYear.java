package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.config.groups.GroupList;
import top.yzljc.qqbot.botkits.image.AbstractImage;

import java.awt.*;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HappyNewYear {

    private static final Logger log = LoggerFactory.getLogger(HappyNewYear.class);

    private static class ImageGen extends AbstractImage {
        public void generate(File outFile) throws IOException {
            // 1. 初始化背景
            initFromBackground("manosaba.png");

            // 2. 准备数据
            LocalDate targetDate = LocalDate.of(2026, 2, 17);
            LocalDate now = LocalDate.now();
            long daysUntil = ChronoUnit.DAYS.between(now, targetDate);
            if (daysUntil < 0) daysUntil = 0;

            String line1 = String.format("距离 %s 年春节还有", now.getYear());
            String line2 = daysUntil + " 天";

            Color red = new Color(229, 31, 86);
            Color shadow = new Color(0, 0, 0, 128);

            // 3. 计算坐标 (利用父类的 g 和 loadFont)
            Font font1 = loadFont(Font.BOLD, 35f);
            g.setFont(font1);
            FontMetrics fm1 = g.getFontMetrics(font1);
            int line1Width = fm1.stringWidth(line1);
            int line1Height = fm1.getHeight();

            Font font2 = loadFont(Font.BOLD, 55f);
            g.setFont(font2);
            FontMetrics fm2 = g.getFontMetrics(font2);
            int line2Width = fm2.stringWidth(line2);
            int line2Height = fm2.getHeight();

            int padding = 20;
            int totalHeight = line1Height + line2Height + 15;

            // 第一行位置
            int line1Y = height - padding - totalHeight + fm1.getAscent() + 10;
            int line1X = (width - line1Width) / 2;

            // 第二行位置
            int line2Y = line1Y + line1Height + 15;
            int line2X = (width - line2Width) / 2;

            // 4. 绘制
            g.setFont(font1);
            drawShadowText(line1, line1X, line1Y, red, shadow);

            g.setFont(font2);
            drawShadowText(line2, line2X, line2Y, red, shadow);

            // 5. 保存
            saveAndDispose(outFile);
        }
    }

    public static void generateDevelopDayImage() throws IOException {
        File tempDir = new File("tmp");
        if (!tempDir.exists()) tempDir.mkdirs();
        File outFile = new File(tempDir, "happynewyear.png");

        // 实例化内部生成器并调用
        new ImageGen().generate(outFile);
    }

    public static void processManodate(JsonNode json) {
        String postType = json.path("post_type").asText("");
        if (!"message".equals(postType)) return;
        String messageType = json.path("message_type").asText("");
        if (!"group".equals(messageType)) return;
        String rawMessage = json.path("raw_message").asText("").trim().toLowerCase();
        long groupId = json.path("group_id").asLong();

        if ("/happynewyear".equals(rawMessage)) {
            sendToSingleGroup(groupId);
            log.info("新年倒计时指令触发图片推送：{}", groupId);
        }
    }

    public static boolean sendToAllGroups() {
        File tempFile = new File("tmp", "happynewyear.png");
        try {
            generateDevelopDayImage();

            byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
            String base64Img = Base64.getEncoder().encodeToString(imgBytes);

            Set<Long> groupIds = GroupList.fetchAllGroupIds();
            if (groupIds.isEmpty()) {
                log.info("未获取到任何群号，跳过推送");
                return false;
            }

            log.info("开始向 {} 个群推送图片……", groupIds.size());
            int count = 0;
            for (Long gid : groupIds) {
                if (!GroupConfigManager.isFeatureEnabled(gid,"new_year")) continue;
                MessageSender.sendGroupMessage(gid, null, base64Img);
                count++;
                try { Thread.sleep(200); } catch (InterruptedException e) {}
            }
            log.info("推送完成，共发送给 {} 个群", count);
            return true;
        } catch (Exception ex) {
            log.error("群发图片异常：{}", ex.getMessage());
            ex.printStackTrace();
            return false;
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    public static void sendToSingleGroup(long targetGroupId) {
        File tempFile = new File("tmp", "happynewyear.png");
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
        Runnable task = HappyNewYear::sendToAllGroups;
        long initialDelay = computeInitialDelayToMidnight11();
        long period = 24 * 60 * 60;
        scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS);
        log.info("已启动每日0:00:01自动推送任务");
    }

    private static long computeInitialDelayToMidnight11() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.toLocalDate().atStartOfDay().plusDays(0).plusSeconds(1);
        if (!now.isBefore(nextRun)) nextRun = nextRun.plusDays(1);
        return Duration.between(now, nextRun).toSeconds();
    }
}