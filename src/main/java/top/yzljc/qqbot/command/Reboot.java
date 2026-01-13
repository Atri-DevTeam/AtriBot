package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 远程重启工具
 * 监听管理员指令，执行外部重启脚本并关闭当前进程
 */
public class Reboot {

    private static final Logger log = LoggerFactory.getLogger(Reboot.class);
    static Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();

    private static final String SCRIPT_NAME = "restart.sh";

    public static void process(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return;
        if (!"group".equals(json.path("message_type").asText())) return;

        long userId = json.path("user_id").asLong();
        String rawMsg = json.path("raw_message").asText().trim();
        long groupId = json.path("group_id").asLong();

        if (admins.contains(userId) && "/reboot".equalsIgnoreCase(rawMsg)) {
            log.info("收到管理员 {} 的重启指令", userId);

            MessageSender.sendGroupMessage(groupId, "收到指令，正在执行重启脚本并终止进程...");

            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    executeRestartScript();
                    log.info("调用系统退出方法：System.exit(0)");
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                    MessageSender.sendGroupMessage(groupId, "重启失败: " + e.getMessage());
                }
            }).start();
        }
    }

    private static void executeRestartScript() throws IOException {
        File scriptFile = new File(SCRIPT_NAME);

        log.debug("检查重启脚本，首选：{}，工作目录：{}", 
                  SCRIPT_NAME, new File(".").getAbsolutePath());
        if (!scriptFile.exists()) {
            log.warn("未找到重启脚本：{}", SCRIPT_NAME);
            scriptFile = new File("restart.bat");
            if (!scriptFile.exists()) {
                throw new IOException(String.format("未找到重启脚本 {} 或 restart.b    at", SCRIPT_NAME));
            }
        }

        log.info("正在执行：{}", scriptFile.getAbsolutePath());

        List<String> command = new ArrayList<>();

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            command.add("cmd.exe");
            command.add("/c");
            command.add("start");
            command.add(scriptFile.getAbsolutePath());
        } else {
            command.add("sh");
            command.add(scriptFile.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();

        // 启动脚本
        pb.start();
    }
}
