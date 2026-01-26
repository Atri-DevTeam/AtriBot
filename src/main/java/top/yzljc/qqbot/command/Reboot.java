package top.yzljc.qqbot.command;

import top.yzljc.qqbot.botkits.message.MessageSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reboot {

    private static final Logger log = LoggerFactory.getLogger(Reboot.class);

    public static void processReboot(long userId, long groupId) {
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
