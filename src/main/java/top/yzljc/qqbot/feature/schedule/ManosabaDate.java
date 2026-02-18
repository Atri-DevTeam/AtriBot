package top.yzljc.qqbot.feature.schedule;

import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.image.AbstractImage;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManosabaDate implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ManosabaDate.class);

    static Settings settings = Config.getInstance();
    private static final long GROUP_ID = settings.getManosabaGroupId();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getGroupId() == settings.getManosabaGroupId()) {
            receiveManodate(sender.getGroupId());
        } else {
            sender.reply("此指令无法在该群聊调用！", false);
        }
        return true;
    }

    private static class ImageGen extends AbstractImage {
        public void generate(File outFile) throws IOException {
            initFromBackground(ConfigFile.IMG_MANOSABA.getFileName());

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
        sendAndNotifyToGroup(GROUP_ID);
        log.info("manodate 指令触发图片推送：{}", groupId);
    }

    public static void sendAndNotifyToGroup() {
        sendAndNotifyToGroup(GROUP_ID);
    }

    public static void sendAndNotifyToGroup(long targetGroupId) {
        File tempFile = new File("tmp", "manoday.png");
        try {
            generateDevelopDayImage();

            byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
            String base64Img = Base64.getEncoder().encodeToString(imgBytes);
            MessageSender.sendGroupMessage(targetGroupId, null, base64Img);

        } catch (Exception ex) {
            log.error("推送图片异常: {}", ex.getMessage());
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }
}