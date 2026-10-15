package top.yzljc.atribot.function.admin;

import top.yzljc.atricore.chat.official.ChatService;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.Identifier;
import top.yzljc.atricore.platform.qq.QQBot;

/**
 * @Author YZ_Ljc_
 * @ClassName AdminPauseCommand
 * @Created_at 2026/07/27
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class AdminPauseCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission()) {
            sender.sendMessage(Identifier.NO_PERMISSION);
            return true;
        }

        if (!(sender instanceof QQCommandSender)) return true;

        if (ChatService.isEmergencyPaused()) {
            ChatService.setEmergencyPaused(false);
            sender.sendMessage(QQBot.BOT_NAME + "已解除暂停状态，机器人恢复正常运行！");
        } else {
            ChatService.setEmergencyPaused(true);
            sender.sendMessage(QQBot.BOT_NAME + "已被暂停使用，维护状态启用！");
        }
        return true;
    }
}