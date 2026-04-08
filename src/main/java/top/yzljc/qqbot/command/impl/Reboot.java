package top.yzljc.qqbot.command.impl;

import top.yzljc.qqbot.chat.GroupMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

public class Reboot implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(Reboot.class);
    static Settings settings = Config.getInstance();
    private static final long debugGroupId = settings.getDebugGroupId();


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令",false);
            return true;
        }

        processReboot(sender.userId(), sender.groupId());

        return true;
    }

    public static void processReboot(){
        processReboot(0, debugGroupId);
    }

    public static void processReboot(long userId, long groupId) {
        log.info("收到管理员 {} 的终止指令", userId);
        GroupMessage.chatMessage(groupId, "收到管理员 "+ userId + " 的终止指令，正在终止进程...");

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