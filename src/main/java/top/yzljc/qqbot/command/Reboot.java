package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reboot {

    private static final Logger log = LoggerFactory.getLogger(Reboot.class);
    static Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();

    public static void process(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return;
        if (!"group".equals(json.path("message_type").asText())) return;

        long userId = json.path("user_id").asLong();
        String rawMsg = json.path("raw_message").asText().trim();
        long groupId = json.path("group_id").asLong();

        if (admins.contains(userId) && "/reboot".equalsIgnoreCase(rawMsg)) {
            log.info("收到管理员 {} 的终止指令", userId);
            MessageSender.sendGroupMessage(groupId, "收到指令，正在终止进程...");

            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    log.info("调用系统退出方法终止当前进程：System.exit(0)");
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                    MessageSender.sendGroupMessage(groupId, "终止失败: " + e.getMessage());
                }
            }).start();
        }
    }
}
