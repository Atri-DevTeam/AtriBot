package top.yzljc.qqbot.feature;

import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botkits.message.MessageSender;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManosabaDate {

    private static final Logger log = LoggerFactory.getLogger(ManosabaDate.class);

    static Settings settings = Config.getInstance();
    private static final long GROUP_ID = settings.getManosabaGroupId();
    private static final List<Long> admins = settings.getAdminUids();

    private static class ImageGen extends AbstractImage {
        public void generate(File outFile) throws IOException {
            initFromBackground("manosaba.png");

            LocalDate start = LocalDate.of(2025, 11, 13);
            LocalDate now = LocalDate.now();
            long days = ChronoUnit.DAYS.between(start, now) + 1;
            if (days < 1) days = 1;

            String[] lines = {
                    "你说的对，但是今天是",
                    "【魔法少女の魔女审判 x Minecraft】项目",
                    "开发的第 " + days + " 天"
            };

            Font font = loadFont(Font.BOLD, 28f);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics(font);

            Color cyan = new Color(0, 200, 255);
            Color shadow = new Color(0, 0, 0, 128);

            int lineHeight = fm.getHeight();
            int padding = 20;
            int totalHeight = lineHeight * lines.length;
            int firstY = height - padding - totalHeight + fm.getAscent();

            for (int i = 0; i < lines.length; ++i) {
                String line = lines[i];
                int textWidth = fm.stringWidth(line);
                int x = (width - textWidth) / 2;
                int y = firstY + i * lineHeight;

                drawShadowText(line, x, y, cyan, shadow);
            }

            saveAndDispose(outFile);
        }
    }

    public static void generateDevelopDayImage() throws IOException {
        File tempDir = new File("tmp");
        if (!tempDir.exists()) tempDir.mkdirs();

        new ImageGen().generate(new File(tempDir, "manoday.png"));
    }

    public static void receiveManodate(long groupId) {
        sendAndNotifyToGroup();
        log.info("manodate 指令触发图片推送：{}", groupId);
    }

    public static boolean sendAndNotifyToGroup() {
        return sendAndNotifyToGroup(GROUP_ID);
    }

    public static boolean sendAndNotifyToGroup(long targetGroupId) {
        File tempFile = new File("tmp", "manoday.png");
        try {
            generateDevelopDayImage();

            byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
            String base64Img = Base64.getEncoder().encodeToString(imgBytes);
            MessageSender.sendGroupMessage(targetGroupId, null, base64Img);
            return true;

        } catch (Exception ex) {
            log.error("推送图片异常: {}", ex.getMessage());
            return false;
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    public static void startAutoDailyTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runnable task = ManosabaDate::sendAndNotifyToGroup;
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