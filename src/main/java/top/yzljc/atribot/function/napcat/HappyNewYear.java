package top.yzljc.atribot.function.napcat;

import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.function.napcat.impl.AbstractImage;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(since = "2.6.1")
public class HappyNewYear implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyNewYear.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!sender.hasPermission()) {
            sendToSingleGroup(sender.getGroupId());
            return true;
        }
        if (Config.getInstance().isDebugMode()) {
            sendToAllGroups();
        } else {
            sendToSingleGroup(sender.getGroupId());
        }
        return true;
    }

    private static class ImageGen extends AbstractImage {
        public void generate(File outFile) throws IOException {
            initFromBackground(Properties.IMG_MANOSABA);
            // ... (unchanged image generation logic)
            LocalDate now = LocalDate.now();
            LocalDate[] cnyDates = {
                    LocalDate.of(2026, 2, 17), LocalDate.of(2027, 2, 6),
                    LocalDate.of(2028, 1, 26), LocalDate.of(2029, 2, 13), LocalDate.of(2030, 2, 3)
            };
            LocalDate targetDate = cnyDates[cnyDates.length - 1];
            for (LocalDate date : cnyDates) {
                if (!date.isBefore(now)) { targetDate = date; break; }
            }
            long daysUntil = ChronoUnit.DAYS.between(now, targetDate);
            String line1, line2;
            if (daysUntil == 0) { line1 = targetDate.getYear() + " 农历新年"; line2 = "春节快乐"; }
            else { line1 = String.format("距离 %s 年春节还有", targetDate.getYear()); line2 = daysUntil + " 天"; }
            Color red = new Color(229, 31, 86), shadow = new Color(0, 0, 0, 128);
            Font font1 = loadFont(Font.BOLD, 35f); g.setFont(font1);
            FontMetrics fm1 = g.getFontMetrics(font1);
            int l1w = fm1.stringWidth(line1), l1h = fm1.getHeight();
            Font font2 = loadFont(Font.BOLD, 55f); g.setFont(font2);
            FontMetrics fm2 = g.getFontMetrics(font2);
            int l2w = fm2.stringWidth(line2), l2h = fm2.getHeight(), padding = 20, th = l1h + l2h + 15;
            int l1y = height - padding - th + fm1.getAscent() + 10, l1x = (width - l1w) / 2;
            int l2y = l1y + l1h + 15, l2x = (width - l2w) / 2;
            g.setFont(font1); drawShadowText(line1, l1x, l1y, red, shadow);
            g.setFont(font2); drawShadowText(line2, l2x, l2y, red, shadow);
            saveAndDispose(outFile);
        }
    }

    public static void generateDevelopDayImage() throws IOException {
        File tempDir = new File("tmp");
        if (!tempDir.exists()) tempDir.mkdirs();
        new ImageGen().generate(new File(tempDir, "happynewyear.png"));
    }

    public static void processHappyNewYear(String groupId) {
        sendToSingleGroup(groupId);
        log.info("新年倒计时指令触发图片推送：{}", groupId);
    }

    public static void sendToAllGroups() {
        File tempFile = new File("tmp", "happynewyear.png");
        ThreadManager.execute(() -> {
            try {
                generateDevelopDayImage();
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                Set<String> groupIds = GroupInformation.fetchAllGroupIds();
                if (groupIds.isEmpty()) { log.info("未获取到任何群号，跳过推送"); return; }
                log.info("开始向 {} 个群推送图片……", groupIds.size());
                int count = 0;
                for (String gid : groupIds) {
                    if (!GroupConfigManager.isFeatureEnabled(gid, "new_year")) continue;
                    GroupMessage.chatMessage(gid, base64Img, MessageUtils.ImageType.BASE64);
                    count++;
                    try { Thread.sleep(200); } catch (InterruptedException e) {}
                }
                log.info("推送完成，共发送给 {} 个群", count);
            } catch (Exception ex) {
                log.error("群发图片异常：{}", ex.getMessage());
            } finally {
                if (tempFile.exists()) tempFile.delete();
            }
        });
    }

    public static void sendToSingleGroup(String targetGroupId) {
        File tempFile = new File("tmp", "happynewyear.png");
        try {
            generateDevelopDayImage();
            if (tempFile.exists()) {
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                GroupMessage.chatMessage(targetGroupId, base64Img, MessageUtils.ImageType.BASE64);
            }
        } catch (Exception e) {
            log.error("发送新年图片到群[{}]失败：{}", targetGroupId, e.getMessage());
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }
}
