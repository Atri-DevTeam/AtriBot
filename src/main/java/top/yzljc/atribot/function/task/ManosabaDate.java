package top.yzljc.atribot.function.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.function.napcat.impl.AbstractImage;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

public class ManosabaDate implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ManosabaDate.class);
    private static final String GROUP_ID = String.valueOf(Config.getInstance().getManosabaGroupId());

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getGroupId().equals(GROUP_ID)) {
            receiveManodate(sender.getGroupId());
        } else {
            sender.sendMessage("此指令无法在该群聊调用！");
        }
        return true;
    }

    private static class ImageGen extends AbstractImage {
        public void generate(File outFile) throws IOException {
            initFromBackground(Properties.IMG_MANOSABA);

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

    public static void receiveManodate(String groupId) {
        sendAndNotifyToGroup(GROUP_ID);
        log.info("manodate 指令触发图片推送：{}", groupId);
    }

    @Schedule(time = "00:00:10", type = ScheduleType.DAILY)
    public static void sendAndNotifyToGroup() {
        sendAndNotifyToGroup(GROUP_ID);
    }

    public static void sendAndNotifyToGroup(String targetGroupId) {
        File tempFile = new File("tmp", "manoday.png");
        try {
            generateDevelopDayImage();

            byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
            String base64Img = Base64.getEncoder().encodeToString(imgBytes);
            GroupMessage.chatMessage(targetGroupId, base64Img, MessageUtils.ImageType.BASE64);

        } catch (Exception ex) {
            log.error("推送图片异常: {}", ex.getMessage());
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }
}
