package top.yzljc.atribot.function.napcat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.Platform;

public class Reboot implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(Reboot.class);
    private static final String debugGroupId = Config.getInstance().getNapcatDebugGroupUin();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!sender.hasPermission()) {
            sender.sendMessage("你没有权限执行此命令");
            return true;
        }
        processReboot(sender.getUserId(), sender.getGroupId());
        return true;
    }

    public static void processReboot() {
        processReboot("Scheduler", debugGroupId);
    }

    public static void processReboot(String userId, String groupId) {
        log.info("收到管理员 {} 的终止指令", userId);
        GroupMessage.chatMessage(groupId, "收到管理员 " + userId + " 的终止指令，正在终止进程...");

        new Thread(() -> {
            try {
                Thread.sleep(1000);
                log.info("调用系统退出方法终止当前进程：System.exit(0)");
                System.exit(0);
            } catch (Exception e) {
                log.warn("终止进程时发生异常: {}", e.getMessage());
                GroupMessage.chatMessage(groupId, "终止失败: " + e.getMessage());
            }
        }).start();
    }
}
