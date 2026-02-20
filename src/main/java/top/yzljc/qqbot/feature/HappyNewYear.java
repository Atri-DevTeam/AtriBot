package top.yzljc.qqbot.feature;

import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.userinfo.GetGroupList;
import top.yzljc.qqbot.botkits.image.AbstractImage;

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

/**
 * 2026春节快乐
 * 在未来的日子里，2027年春节到来之前，我们将以日历的方式进行推送
 * “伊甸一号”，你可以休息了
 */
@Deprecated(since = "2.6.1")
public class HappyNewYear implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyNewYear.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()){
            sendToSingleGroup(sender.getGroupId());
            return true;
        }
        if (sender.isDebug()){
            sendToAllGroups();
        }else {
            sendToSingleGroup(sender.getGroupId());
        }
        return true;
    }

    private static class ImageGen extends AbstractImage {
        public void generate(File outFile) throws IOException {
            initFromBackground(ConfigFile.IMG_MANOSABA.getFileName());

            LocalDate now = LocalDate.now();

            LocalDate[] cnyDates = {
                    LocalDate.of(2026, 2, 17),
                    LocalDate.of(2027, 2, 6),
                    LocalDate.of(2028, 1, 26),
                    LocalDate.of(2029, 2, 13),
                    LocalDate.of(2030, 2, 3)
            };

            LocalDate targetDate = cnyDates[cnyDates.length - 1]; // 默认最后一个
            for (LocalDate date : cnyDates) {
                if (!date.isBefore(now)) { // date >= now
                    targetDate = date;
                    break;
                }
            }

            long daysUntil = ChronoUnit.DAYS.between(now, targetDate);

            String line1;
            String line2;

            if (daysUntil == 0) {
                // 春节当天
                line1 = targetDate.getYear() + " 农历新年";
                line2 = "春节快乐";
            } else {
                // 倒计时模式
                line1 = String.format("距离 %s 年春节还有", targetDate.getYear());
                line2 = daysUntil + " 天";
            }

            Color red = new Color(229, 31, 86);
            Color shadow = new Color(0, 0, 0, 128);

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

            g.setFont(font1);
            drawShadowText(line1, line1X, line1Y, red, shadow);

            g.setFont(font2);
            drawShadowText(line2, line2X, line2Y, red, shadow);

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

    public static void processHappyNewYear(long groupId) {
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

                Set<Long> groupIds = GetGroupList.fetchAllGroupIds();
                if (groupIds.isEmpty()) {
                    log.info("未获取到任何群号，跳过推送");
                    return;
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
            } catch (Exception ex) {
                log.error("群发图片异常：{}", ex.getMessage());
            } finally {
                if (tempFile.exists()) tempFile.delete();
            }
        });
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
            log.error("发送新年图片到群[{}]失败：{}", targetGroupId, e.getMessage());
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }
}