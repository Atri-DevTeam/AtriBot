package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;

import java.util.List;

public class Reboot {

    static Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();

    public static void process(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return;
        if (!"group".equals(json.path("message_type").asText())) return;

        long userId = json.path("user_id").asLong();
        String rawMsg = json.path("raw_message").asText().trim();
        long groupId = json.path("group_id").asLong();

        if (admins.contains(userId) && "/reboot".equalsIgnoreCase(rawMsg)) {
            System.out.println("[Reboot] 收到管理员 " + userId + " 的终止指令");

            MessageSender.sendGroupMessage(groupId, "收到指令，正在终止进程...");

            new Thread(() -> {
                try {
                    Thread.sleep(1000);

                    System.out.println("[Reboot] 正在终止当前进程 (System.exit 0)");
                    System.exit(0);

                } catch (Exception e) {
                    e.printStackTrace();
                    MessageSender.sendGroupMessage(groupId, "终止失败: " + e.getMessage());
                }
            }).start();
        }
    }
}